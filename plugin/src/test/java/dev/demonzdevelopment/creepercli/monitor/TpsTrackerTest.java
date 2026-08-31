

package dev.demonzdevelopment.creepercli.monitor;

import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TpsTrackerTest {

    @Test
    void proxyPlatformProducesUnsupportedSnapshot() {
        CreeperCLIPlugin plugin = mock(CreeperCLIPlugin.class);
        dev.demonzdevelopment.creepercli.platform.Platform proxy = mock(dev.demonzdevelopment.creepercli.platform.Platform.class);
        when(proxy.tickLoopSupported()).thenReturn(false);
        when(plugin.platform()).thenReturn(proxy);

        TpsTracker tracker = new TpsTracker(plugin);
        tracker.start();

        assertFalse(tracker.running(), "no sampler on a proxy platform");
        var snap = tracker.snapshot();
        assertEquals(false, snap.get("supported").getAsBoolean());
        assertEquals(-1, snap.get("tps1m").getAsInt());
        assertEquals(-1, snap.get("tps5m").getAsInt());
        assertEquals(-1, snap.get("tps15m").getAsInt());
    }

    @Test
    void bukkitPlatformUsesProvidedSampler() {
        CreeperCLIPlugin plugin = mock(CreeperCLIPlugin.class);
        dev.demonzdevelopment.creepercli.platform.Platform bukkit = mock(dev.demonzdevelopment.creepercli.platform.Platform.class);
        when(bukkit.tickLoopSupported()).thenReturn(true);

        when(bukkit.startTickSampler(org.mockito.ArgumentMatchers.any())).thenReturn((AutoCloseable) () -> {});

        when(plugin.platform()).thenReturn(bukkit);

        TpsTracker tracker = new TpsTracker(plugin);
        tracker.start();

        assertTrue(tracker.running(), "sampler should be active on Bukkit-family");
        tracker.stop();
        assertFalse(tracker.running());
    }

    @Test
    void emptySamplesDefaultsToMaxTps() {
        CreeperCLIPlugin plugin = mock(CreeperCLIPlugin.class);
        dev.demonzdevelopment.creepercli.platform.Platform proxy = mock(dev.demonzdevelopment.creepercli.platform.Platform.class);
        when(proxy.tickLoopSupported()).thenReturn(false);
        when(plugin.platform()).thenReturn(proxy);

        TpsTracker tracker = new TpsTracker(plugin);
        
        assertTrue(Math.abs(tracker.tps(60) - 20.0) < 1e-9, "no samples defaults to full TPS");
    }
}