package dev.demonzdevelopment.creepercli.net;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineReaderTest {
    @Test
    void slowPartialFrameCannotExtendDeadline() throws Exception {
        try (ServerSocket listener = new ServerSocket(0);
             Socket client = new Socket("127.0.0.1", listener.getLocalPort());
             Socket server = listener.accept()) {
            server.setSoTimeout(5000);
            Thread sender = new Thread(() -> {
                try {
                    for (int i = 0; i < 60; i++) {
                        client.getOutputStream().write('{');
                        client.getOutputStream().flush();
                        Thread.sleep(50);
                    }
                    client.getOutputStream().write('\n');
                } catch (IOException ignored) {
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
            long started = System.nanoTime();
            sender.start();
            try {
                LineReader reader = new LineReader(server.getInputStream(), 4096);
                assertThrows(SocketTimeoutException.class,
                        () -> reader.readLine(server, started + TimeUnit.MILLISECONDS.toNanos(500)));
                assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(2),
                        "Partial bytes must not extend the authentication deadline");
            } finally {
                sender.interrupt();
                sender.join(1000);
            }
        }
    }

    @Test
    void silentClientTimesOutAtDeadlineInsteadOfSocketIdleTimeout() throws Exception {
        try (ServerSocket listener = new ServerSocket(0);
             Socket client = new Socket("127.0.0.1", listener.getLocalPort());
             Socket server = listener.accept()) {
            server.setSoTimeout(5000);
            client.getOutputStream().write('{');
            client.getOutputStream().flush();
            LineReader reader = new LineReader(server.getInputStream(), 4096);
            long started = System.nanoTime();
            assertThrows(SocketTimeoutException.class,
                    () -> reader.readLine(server, started + TimeUnit.MILLISECONDS.toNanos(250)));
            assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(2));
        }
    }

    @Test
    void expiredDeadlineRejectsAnAlreadyAvailableFrame() throws Exception {
        try (Socket socket = new Socket()) {
            LineReader reader = new LineReader(
                    new ByteArrayInputStream("request\n".getBytes(StandardCharsets.UTF_8)), 4096);
            assertThrows(SocketTimeoutException.class,
                    () -> reader.readLine(socket, System.nanoTime() - 1));
        }
    }

    @Test
    void completeFrameBeforeDeadlineSucceeds() throws Exception {
        try (Socket socket = new Socket()) {
            socket.setSoTimeout(5000);
            LineReader reader = new LineReader(
                    new ByteArrayInputStream("first\r\nsecond\n".getBytes(StandardCharsets.UTF_8)), 4096);
            assertEquals("first", reader.readLine(socket, System.nanoTime() + TimeUnit.SECONDS.toNanos(5)));
            socket.setSoTimeout(5000);
            assertEquals("second", reader.readLine());
            assertEquals(5000, socket.getSoTimeout());
        }
    }
}
