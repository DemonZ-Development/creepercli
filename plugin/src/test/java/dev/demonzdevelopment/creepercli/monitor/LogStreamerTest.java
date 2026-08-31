

package dev.demonzdevelopment.creepercli.monitor;

import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.CreeperError;
import dev.demonzdevelopment.creepercli.PluginConfig;
import dev.demonzdevelopment.creepercli.net.ClientConnection;
import org.junit.jupiter.api.Test;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LogStreamerTest {

    private CreeperCLIPlugin pluginWithBuffer(int lines) {
        CreeperCLIPlugin plugin = mock(CreeperCLIPlugin.class);
        PluginConfig cfg = mock(PluginConfig.class);
        Logger logger = Logger.getLogger("LogStreamerTest-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        when(plugin.cfg()).thenReturn(cfg);
        when(cfg.logBufferLines()).thenReturn(lines);
        when(plugin.platform()).thenReturn(new dev.demonzdevelopment.creepercli.platform.Platform() {
            @Override public String family() { return "test"; }
            @Override public String software() { return "Test"; }
            @Override public String serverVersion() { return "0"; }
            @Override public Logger logger() { return logger; }
            @Override public java.nio.file.Path dataFolder() { return java.nio.file.Path.of("/"); }
            @Override public boolean tickLoopSupported() { return false; }
            @Override public AutoCloseable startTickSampler(Runnable onTick) { return null; }
            @Override public dev.demonzdevelopment.creepercli.platform.ConsoleBridge consoleBridge(dev.demonzdevelopment.creepercli.CreeperCLIPlugin core) {
                return (cmd, timeout) -> java.util.concurrent.CompletableFuture.completedFuture(
                        new dev.demonzdevelopment.creepercli.platform.ConsoleBridge.Result(false, java.util.List.of(), 0L));
            }
        });
        return plugin;
    }

    private static LogRecord record(String level, String msg) {
        LogRecord r = new LogRecord(Level.parse(level), msg);
        r.setMillis(System.currentTimeMillis());
        return r;
    }

    @Test
    void ringBufferIsBoundedByConfiguredSize() {
        CreeperCLIPlugin plugin = pluginWithBuffer(5);
        LogStreamer streamer = new LogStreamer(plugin);
        Handler h = (Handler) getHandler(streamer);
        for (int i = 0; i < 100; i++) {
            h.publish(record("INFO", "msg-" + i));
        }
        assertEquals(5, streamer.buffer().size());
        assertTrue(streamer.buffer().get(4).contains("msg-99"));
    }

    @Test
    void sinceReturnsOnlyNewLinesAfterMark() {
        CreeperCLIPlugin plugin = pluginWithBuffer(100);
        LogStreamer streamer = new LogStreamer(plugin);
        Handler h = (Handler) getHandler(streamer);
        for (int i = 0; i < 10; i++) h.publish(record("INFO", "before-" + i));
        long mark = streamer.mark();
        for (int i = 0; i < 5; i++) h.publish(record("INFO", "after-" + i));
        var since = streamer.since(mark);
        assertEquals(5, since.size());
        assertTrue(since.get(0).contains("after-0"));
        assertTrue(since.get(4).contains("after-4"));
    }

    @Test
    void subscribeWithGrepFiltersOutNonMatchingEvents() throws Exception {
        CreeperCLIPlugin plugin = pluginWithBuffer(100);
        LogStreamer streamer = new LogStreamer(plugin);
        Handler h = (Handler) getHandler(streamer);
        streamer.attach();
        try {
            ClientConnection conn = mock(ClientConnection.class);
            streamer.subscribe(conn, "needle");
            
            
            assertEquals(1, streamer.subscriberCount());
            
            try {
                streamer.subscribe(conn, "[unclosed");
            } catch (CreeperError err) {
                assertEquals(dev.demonzdevelopment.creepercli.Protocol.ERR_INVALID_PARAMS, err.code());
            }
        } finally {
            streamer.detach();
        }
    }

    @Test
    void unsubscribeAndUnsubscribeAllRemoveSubscribers() throws Exception {
        CreeperCLIPlugin plugin = pluginWithBuffer(100);
        LogStreamer streamer = new LogStreamer(plugin);
        ClientConnection a = mock(ClientConnection.class);
        ClientConnection b = mock(ClientConnection.class);
        streamer.subscribe(a, null);
        streamer.subscribe(b, "x");
        assertEquals(2, streamer.subscriberCount());
        streamer.unsubscribe(a);
        assertEquals(1, streamer.subscriberCount());
        streamer.unsubscribeAll();
        assertEquals(0, streamer.subscriberCount());
    }

    @Test
    void feedBridgesLog4jLevelsCorrectly() {
        CreeperCLIPlugin plugin = pluginWithBuffer(50);
        LogStreamer streamer = new LogStreamer(plugin);
        
        streamer.feed("ERROR", "boom", System.currentTimeMillis());
        streamer.feed("WARN", "careful", System.currentTimeMillis());
        streamer.feed("INFO", "normal", System.currentTimeMillis());
        var lines = streamer.buffer();
        assertEquals(3, lines.size());
        assertTrue(lines.get(0).contains("SEVERE"));
        assertTrue(lines.get(1).contains("WARNING"));
        assertTrue(lines.get(2).contains("INFO"));
    }

    private static Object getHandler(LogStreamer streamer) {
        try {
            var f = LogStreamer.class.getDeclaredField("handler");
            f.setAccessible(true);
            return f.get(streamer);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}