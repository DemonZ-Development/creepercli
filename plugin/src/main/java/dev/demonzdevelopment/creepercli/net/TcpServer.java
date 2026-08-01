package dev.demonzdevelopment.creepercli.net;

import com.google.gson.JsonObject;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.Json;
import dev.demonzdevelopment.creepercli.Protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TcpServer {
    private final CreeperCLIPlugin plugin;
    private final Set<ClientConnection> connections = ConcurrentHashMap.newKeySet();
    private ServerSocket serverSocket;
    private Thread acceptThread;

    public TcpServer(CreeperCLIPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() throws IOException {
        String host = plugin.cfg().networkHost();
        int port = plugin.cfg().networkPort();
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(host, port), 64);
        acceptThread = new Thread(this::acceptLoop, "creepercli-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        plugin.getLogger().info("CreeperCLI TCP listener bound to " + host + ":" + port);
    }

    private void acceptLoop() {
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                if (connections.size() >= plugin.cfg().maxConnections()) {
                    try (Socket s = socket) {
                        JsonObject error = new JsonObject();
                        error.addProperty("v", Protocol.VERSION);
                        error.addProperty("type", "response");
                        error.addProperty("ok", false);
                        JsonObject e = new JsonObject();
                        e.addProperty("code", Protocol.ERR_SERVER_FULL);
                        e.addProperty("message", "Server connection limit reached");
                        error.add("error", e);
                        OutputStream out = s.getOutputStream();
                        out.write(Json.GSON.toJson(error).getBytes(StandardCharsets.UTF_8));
                        out.write('\n');
                        out.flush();
                    } catch (IOException ignored) {
                    }
                    continue;
                }
                ClientConnection conn = new ClientConnection(plugin, socket);
                connections.add(conn);
                conn.start();
            } catch (SocketException e) {
                if (serverSocket.isClosed()) break;
            } catch (IOException e) {
                if (serverSocket.isClosed()) break;
                plugin.getLogger().warning("Accept error: " + e.getMessage());
            }
        }
    }

    public void remove(ClientConnection conn) {
        connections.remove(conn);
    }

    public int connectionCount() {
        return connections.size();
    }

    public void stop() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
        for (ClientConnection conn : connections) {
            conn.close();
        }
        connections.clear();
    }
}
