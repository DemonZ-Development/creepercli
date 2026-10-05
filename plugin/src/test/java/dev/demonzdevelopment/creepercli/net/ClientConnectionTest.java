package dev.demonzdevelopment.creepercli.net;

import com.google.gson.JsonObject;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.Json;
import dev.demonzdevelopment.creepercli.PluginConfig;
import dev.demonzdevelopment.creepercli.commands.CommandRouter;
import dev.demonzdevelopment.creepercli.security.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientConnectionTest {
    private CreeperCLIPlugin plugin;
    private CountDownLatch closed;

    @BeforeEach
    void setUp() {
        plugin = mock(CreeperCLIPlugin.class);
        PluginConfig cfg = mock(PluginConfig.class);
        when(plugin.cfg()).thenReturn(cfg);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ClientConnectionTest"));
        when(cfg.handshakeTimeoutMillis()).thenReturn(500);
        when(cfg.sessionTimeoutMillis()).thenReturn(5000);
        when(cfg.maxPayloadBytes()).thenReturn(4096);
        closed = new CountDownLatch(1);
        doAnswer(invocation -> {
            closed.countDown();
            return null;
        }).when(plugin).onConnectionClosed(any());
    }

    @Test
    void slowUnauthenticatedFrameClosesConnectionAtDeadline() throws Exception {
        try (ServerSocket listener = new ServerSocket(0);
             Socket client = new Socket("127.0.0.1", listener.getLocalPort());
             Socket server = listener.accept()) {
            ClientConnection connection = new ClientConnection(plugin, server);
            Thread sender = new Thread(() -> {
                try {
                    for (int i = 0; i < 60; i++) {
                        client.getOutputStream().write('{');
                        client.getOutputStream().flush();
                        Thread.sleep(50);
                    }
                } catch (IOException ignored) {
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
            connection.start();
            sender.start();
            try {
                assertTrue(closed.await(2, TimeUnit.SECONDS),
                        "A slow partial frame must release the connection slot");
                assertTrue(server.isClosed());
            } finally {
                connection.close();
                sender.interrupt();
                sender.join(1000);
            }
        }
    }

    @Test
    void authenticatedConnectionWorksBeyondHandshakeDeadline() throws Exception {
        CommandRouter router = mock(CommandRouter.class);
        when(plugin.router()).thenReturn(router);
        JsonObject data = new JsonObject();
        data.addProperty("pong", true);
        when(router.route(any(), eq("ping"), any()))
                .thenReturn(CompletableFuture.completedFuture(data));
        try (ServerSocket listener = new ServerSocket(0);
             Socket client = new Socket("127.0.0.1", listener.getLocalPort());
             Socket server = listener.accept()) {
            ClientConnection connection = new ClientConnection(plugin, server);
            connection.setSession(new Session("token", "admin", "127.0.0.1", new RateLimiter(100, 100)));
            connection.start();
            try {
                Thread.sleep(650);
                client.getOutputStream().write(
                        "{\"v\":1,\"type\":\"request\",\"id\":\"alive\",\"action\":\"ping\"}\n"
                                .getBytes(StandardCharsets.UTF_8));
                client.getOutputStream().flush();
                client.setSoTimeout(2000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        client.getInputStream(), StandardCharsets.UTF_8));
                JsonObject response = Json.parseObject(reader.readLine());
                assertEquals("alive", response.get("id").getAsString());
                assertTrue(response.get("ok").getAsBoolean());
                assertTrue(response.getAsJsonObject("data").get("pong").getAsBoolean());
            } finally {
                connection.close();
            }
        }
    }
}
