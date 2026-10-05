

package dev.demonzdevelopment.creepercli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PluginConfigTest {

    @TempDir
    Path tempDir;

    private CreeperCLIPlugin plugin;
    private Path dataDir;

    @BeforeEach
    void setUp() throws IOException {
        dataDir = tempDir.resolve("plugins").resolve("CreeperCLI");
        Files.createDirectories(dataDir);
        try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
            Files.copy(in, dataDir.resolve("config.yml"));
        }
        plugin = mock(CreeperCLIPlugin.class);
        when(plugin.dataFolder()).thenReturn(dataDir);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("PluginConfigTest"));
    }

    @Test
    void testDefaultsLoadFromShippedConfig() {
        PluginConfig cfg = new PluginConfig(plugin);

        assertEquals(2, cfg.configVersion());
        assertEquals("127.0.0.1", cfg.networkHost());
        assertEquals(45678, cfg.networkPort());
        assertEquals(16, cfg.maxConnections());
        assertEquals(30, cfg.execTimeoutSeconds());
        assertEquals(List.of("list", "whitelist *", "say *", "restart"), cfg.execAllowlist());
        assertEquals(30.0, cfg.commandsPerSecond());
        assertEquals(30_000, cfg.handshakeTimeoutMillis());
        assertFalse(cfg.debugLog());
    }

    @Test
    void testSingleSegmentKeyRoundTrip() {
        PluginConfig cfg = new PluginConfig(plugin);
        cfg.set("config-version", 7);
        cfg.save();

        String raw = readConfig();
        assertFalse(raw.contains("\"\":"),
                "single-segment set must not create an empty-keyed section");

        assertEquals(7, new PluginConfig(plugin).configVersion());
    }

    @Test
    void testScalarLeadsAndNestedSetSurviveReload() throws IOException {
        PluginConfig cfg = new PluginConfig(plugin);
        assertTrue(cfg.getInt("network.port", 0) != 0, "scalar leaves must be readable");
        cfg.set("monitor.log-buffer-lines", 250);
        cfg.set("brand-new.key", "hello");
        cfg.save();
        replaceConfig("config-version: 1\n" + readConfig());

        PluginConfig reloaded = new PluginConfig(plugin);
        assertEquals(250, reloaded.getInt("monitor.log-buffer-lines", 0));
        assertEquals("hello", reloaded.getString("brand-new.key", ""));
    }

    @Test
    void testMigrationWritesBackWhenVersionOlder() throws IOException {
        replaceConfig("config-version: 0\n");

        new PluginConfig(plugin);

        String raw = readConfig();
        assertTrue(raw.contains("config-version: " + PluginConfig.CURRENT_CONFIG_VERSION));
        assertTrue(raw.contains("debug-log: false"), "migration must inject missing defaults");
        assertTrue(raw.contains("server-root"));
    }

    @Test
    void testServerRootExplicitPath() {
        PluginConfig cfg = new PluginConfig(plugin);
        cfg.set("sandbox.server-root", tempDir.toString());
        assertEquals(tempDir.toAbsolutePath().normalize(), cfg.serverRoot());
    }

    @Test
    void testServerRootFallsBackToServerDirWhenUnset() throws IOException {
        
        
        replaceConfig("config-version: 1\nnetwork:\n  host: \"127.0.0.1\"\n");

        PluginConfig cfg = new PluginConfig(plugin);

        assertEquals(dataDir.getParent().getParent().toAbsolutePath().normalize(),
                cfg.serverRoot());
    }

    @Test
    void invalidConfigIsBackedUpBeforeDefaultsAreRestored() throws IOException {
        replaceConfig("network: [unterminated\nsecret-setting: keep-me\n");

        PluginConfig cfg = new PluginConfig(plugin);

        assertEquals("127.0.0.1", cfg.networkHost());
        try (var files = Files.list(dataDir)) {
            Path backup = files.filter(p -> p.getFileName().toString().startsWith("config.yml.invalid-"))
                    .findFirst().orElseThrow();
            assertTrue(Files.readString(backup).contains("secret-setting: keep-me"));
        }
    }

    private String readConfig() {
        try {
            return Files.readString(dataDir.resolve("config.yml"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void replaceConfig(String content) throws IOException {
        Files.writeString(dataDir.resolve("config.yml"), content);
    }
}
