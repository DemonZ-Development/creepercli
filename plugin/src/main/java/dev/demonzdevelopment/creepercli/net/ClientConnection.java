

package dev.demonzdevelopment.creepercli.net;

import com.google.gson.JsonObject;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.CreeperError;
import dev.demonzdevelopment.creepercli.Json;
import dev.demonzdevelopment.creepercli.Protocol;
import dev.demonzdevelopment.creepercli.sandbox.PathEscapeException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class ClientConnection implements Runnable {
    private static final int OUTBOUND_QUEUE_CAPACITY = 256;

    private final CreeperCLIPlugin plugin;
    private final Socket socket;
    private final String remoteIp;
    private final OutputStream out;
    private final long authenticationDeadlineNanos;
    private final BlockingQueue<String> outbound = new ArrayBlockingQueue<>(OUTBOUND_QUEUE_CAPACITY);
    private volatile Session session;
    private volatile boolean running = true;
    private Thread writerThread;

    public ClientConnection(CreeperCLIPlugin plugin, Socket socket) throws IOException {
        this.plugin = plugin;
        this.socket = socket;
        this.socket.setKeepAlive(true);
        this.socket.setTcpNoDelay(true);
        this.socket.setSoTimeout(plugin.cfg().handshakeTimeoutMillis());
        this.remoteIp = socket.getInetAddress().getHostAddress();
        this.out = socket.getOutputStream();
        this.authenticationDeadlineNanos = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(plugin.cfg().handshakeTimeoutMillis());
    }

    public void start() {
        writerThread = new Thread(this::writerLoop, "creepercli-writer-" + remoteIp);
        writerThread.setDaemon(true);
        writerThread.start();
        Thread thread = new Thread(this, "creepercli-conn-" + remoteIp);
        thread.setDaemon(true);
        thread.start();
    }

    public String remoteIp() {
        return remoteIp;
    }

    public Session session() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
        try {
            socket.setSoTimeout(session == null
                    ? plugin.cfg().handshakeTimeoutMillis()
                    : plugin.cfg().sessionTimeoutMillis());
        } catch (SocketException e) {
            close();
        }
    }

    public InetAddress address() {
        return socket.getInetAddress();
    }

    public Path resolve(String requested) throws CreeperError {
        String cwd = session == null ? "" : session.cwd;
        try {
            return plugin.sanitizer().resolve(cwd, requested);
        } catch (PathEscapeException e) {
            throw new CreeperError(Protocol.ERR_PATH_ESCAPE, e.getMessage());
        }
    }

    public String jailPath(Path p) {
        return plugin.sanitizer().toJailPath(p);
    }

    @Override
    public void run() {
        try {
            LineReader reader = new LineReader(socket.getInputStream(), plugin.cfg().maxPayloadBytes());
            while (running) {
                String line = session == null
                        ? reader.readLine(socket, authenticationDeadlineNanos)
                        : reader.readLine();
                if (line == null) break;
                handleLine(line);
            }
        } catch (SocketTimeoutException ignored) {
        } catch (SocketException ignored) {
        } catch (IOException e) {
            if (running) plugin.getLogger().warning("Connection error from " + remoteIp + ": " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("Unhandled error on connection " + remoteIp + ": " + e);
        } finally {
            close();
        }
    }

    private void handleLine(String line) {
        String id;
        String action;
        JsonObject params;
        try {
            JsonObject req = Json.parseObject(line);
            int version = Json.optInt(req, "v", -1);
            String type = Json.opt(req, "type", "");
            if (version != Protocol.VERSION) {
                throw new CreeperError(Protocol.ERR_BAD_REQUEST,
                        "Unsupported protocol version " + version + " (expected " + Protocol.VERSION + ")");
            }
            if (!"request".equals(type)) {
                throw new CreeperError(Protocol.ERR_BAD_REQUEST, "Frame type must be request");
            }
            id = Json.opt(req, "id", null);
            action = Json.opt(req, "action", "");
            if (id == null || id.isBlank()) {
                throw new CreeperError(Protocol.ERR_BAD_REQUEST, "Request id is required");
            }
            if (action.isBlank()) {
                throw new CreeperError(Protocol.ERR_BAD_REQUEST, "Action is required");
            }
            JsonObject p = req.has("params") && req.get("params").isJsonObject() ? req.getAsJsonObject("params") : new JsonObject();
            params = p;
        } catch (CreeperError e) {
            sendError(null, e.code(), e.getMessage());
            return;
        }
        try {
            CompletableFuture<JsonObject> future = plugin.router().route(this, action, params);
            future.whenComplete((data, err) -> {
                if (err != null) {
                    if (err instanceof CreeperError ce) {
                        sendError(id, ce.code(), ce.getMessage());
                    } else {
                        plugin.getLogger().warning("Async error for " + action + ": " + err);
                        sendError(id, Protocol.ERR_INTERNAL, "Internal server error");
                    }
                } else {
                    sendResponse(id, data);
                }
            });
        } catch (CreeperError e) {
            sendError(id, e.code(), e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("Routing error for " + action + ": " + e);
            sendError(id, Protocol.ERR_INTERNAL, "Internal server error");
        }
    }

    public void sendResponse(String id, JsonObject data) {
        JsonObject resp = new JsonObject();
        resp.addProperty("v", Protocol.VERSION);
        resp.addProperty("type", "response");
        if (id != null) resp.addProperty("id", id);
        resp.addProperty("ok", true);
        resp.add("data", data == null ? new JsonObject() : data);
        write(resp);
    }

    public void sendError(String id, String code, String message) {
        JsonObject resp = new JsonObject();
        resp.addProperty("v", Protocol.VERSION);
        resp.addProperty("type", "response");
        if (id != null) resp.addProperty("id", id);
        resp.addProperty("ok", false);
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message == null ? code : message);
        resp.add("error", error);
        write(resp);
    }

    public void sendEvent(String event, JsonObject data) {
        JsonObject frame = new JsonObject();
        frame.addProperty("v", Protocol.VERSION);
        frame.addProperty("type", "event");
        frame.addProperty("event", event);
        frame.add("data", data == null ? new JsonObject() : data);
        write(frame);
    }

    private void write(JsonObject frame) {
        if (!running) return;
        String encoded = Json.GSON.toJson(frame) + "\n";
        if (encoded.getBytes(StandardCharsets.UTF_8).length > plugin.cfg().maxPayloadBytes()) {
            if (frame.has("id")) {
                JsonObject fallback = new JsonObject();
                fallback.addProperty("v", Protocol.VERSION);
                fallback.addProperty("type", "response");
                fallback.add("id", frame.get("id"));
                fallback.addProperty("ok", false);
                JsonObject error = new JsonObject();
                error.addProperty("code", Protocol.ERR_PAYLOAD_TOO_LARGE);
                error.addProperty("message", "Response exceeds configured payload limit");
                fallback.add("error", error);
                encoded = Json.GSON.toJson(fallback) + "\n";
            } else {
                plugin.getLogger().warning("Dropped oversized CreeperCLI event for " + remoteIp);
                return;
            }
        }
        if (!outbound.offer(encoded)) {
            plugin.getLogger().warning("Closing slow CreeperCLI client " + remoteIp
                    + " after outbound queue reached " + OUTBOUND_QUEUE_CAPACITY + " frames");
            close();
        }
    }

    private void writerLoop() {
        try {
            while (running || !outbound.isEmpty()) {
                String encoded = outbound.poll(1, TimeUnit.SECONDS);
                if (encoded == null) continue;
                out.write(encoded.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            close();
        }
    }

    public synchronized void close() {
        if (!running) return;
        running = false;
        if (writerThread != null && writerThread != Thread.currentThread()) {
            writerThread.interrupt();
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
        plugin.onConnectionClosed(this);
    }
}
