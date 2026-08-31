

package dev.demonzdevelopment.creepercli.monitor;

import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.net.ClientConnection;
import dev.demonzdevelopment.creepercli.net.Session;
import dev.demonzdevelopment.creepercli.PluginConfig;
import dev.demonzdevelopment.creepercli.platform.Platform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatsCollectorTest {

    @Test
    void collectReturnsPopulatedJson() {
        CreeperCLIPlugin plugin = mock(CreeperCLIPlugin.class);
        Platform platform = mock(Platform.class);
        when(plugin.platform()).thenReturn(platform);
        when(plugin.uptimeMillis()).thenReturn(1234L);
        when(plugin.serverRoot()).thenReturn(java.nio.file.Path.of(System.getProperty("java.io.tmpdir")));

        StatsCollector stats = new StatsCollector(plugin);
        var json = stats.collect();

        assertNotNull(json);
        assertEquals(1234L, json.get("uptimeMs").getAsLong());
        assertNotNull(json.getAsJsonObject("memory"));
        assertNotNull(json.getAsJsonObject("cpu"));
        assertNotNull(json.getAsJsonObject("disk"));
        assertNotNull(json.getAsJsonObject("jvm"));
        assertTrue(json.getAsJsonObject("jvm").get("availableProcessors").getAsLong() > 0);
    }

    @Test
    void collectGracefullyHandlesMissingServerRoot() {
        CreeperCLIPlugin plugin = mock(CreeperCLIPlugin.class);
        Platform platform = mock(Platform.class);
        when(plugin.platform()).thenReturn(platform);
        when(plugin.serverRoot()).thenReturn(java.nio.file.Path.of("/this/does/not/exist/at/all/zzz"));
        when(plugin.uptimeMillis()).thenReturn(0L);

        StatsCollector stats = new StatsCollector(plugin);
        var json = stats.collect();
        var disk = json.getAsJsonObject("disk");
        
        assertEquals(0L, disk.get("total").getAsLong());
    }
}