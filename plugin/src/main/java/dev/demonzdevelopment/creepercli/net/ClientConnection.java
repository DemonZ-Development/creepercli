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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class ClientConnection implements Runnable {
    private final CreeperCLIPlugin plugin;
    private final Socket socket;
    private final String remoteIp;
    private final OutputStream out;
    private volatile Session session;
    private volatile boolean running = true;

    public ClientConnection(CreeperCLIPlugin plugin, Socket socket) throws IOException {
        this.plugin = plugin;
        this.socket = socket;
        this.socket.setKeepAlive(true);
        this.socket.setTcpNoDelay(true);
        this.remoteIp = socket.getInetAddress().getHostAddress();
        this.out = socket.getOutputStream();
    }

    public void start() {
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
    }

    public InetAddress address() {
        return socket.getInetAddress();
    }

    public Path resolve(String requested) throws PathEscapeException {
        String cwd = session == null ? "" : session.cwd;
        return plugin.sanitizer().resolve(cwd, requested);
    }

    public String jailPath(Path p) {
        return plugin.sanitizer().toJailPath(p);
    }

    @Override
    public void run() {
        try {
            LineReader reader = new LineReader(socket.getInputStream(), plugin.cfg().maxPayloadBytes());
            String line;
            while (running && (line = reader.readLine()) != null) {
                handleLine(line);
            }
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
            id = Json.opt(req, "id", null);
            action = Json.opt(req, "action", "");
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
        try {
            synchronized (out) {
                out.write(Json.GSON.toJson(frame).getBytes(StandardCharsets.UTF_8));
                out.write('\n');
                out.flush();
            }
        } catch (IOException e) {
            close();
        }
    }

    public void close() {
        if (!running) return;
        running = false;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
        plugin.onConnectionClosed(this);
    }
}
