

package dev.demonzdevelopment.creepercli.security;

import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.PluginConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditLoggerTest {

    @Test
    void logsAreAppendedToConfiguredFile(@TempDir Path tmp) throws Exception {
        CreeperCLIPlugin plugin = mock(CreeperCLIPlugin.class);
        PluginConfig cfg = mock(PluginConfig.class);
        when(plugin.cfg()).thenReturn(cfg);
        when(cfg.auditMaxMb()).thenReturn(1);
        Path data = tmp.resolve("CreeperCLI");
        Files.createDirectories(data);
        when(plugin.dataFolder()).thenReturn(data);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("AuditLoggerTest"));

        AuditLogger audit = new AuditLogger(plugin);
        audit.start();
        audit.log("127.0.0.1", "admin", "auth.login", "success");
        audit.log("127.0.0.1", "admin", "fs.cat", "/path");
        
        Thread.sleep(200);
        audit.stop();

        
        String content = Files.readString(data.resolve("creepercli-audit.log"));
        assertTrue(content.contains("auth.login"));
        assertTrue(content.contains("fs.cat"));
        assertTrue(content.contains("admin"));
    }

    @Test
    void linesAreTruncatedTo500Chars(@TempDir Path tmp) throws Exception {
        CreeperCLIPlugin plugin = mock(CreeperCLIPlugin.class);
        PluginConfig cfg = mock(PluginConfig.class);
        when(plugin.cfg()).thenReturn(cfg);
        when(cfg.auditMaxMb()).thenReturn(1);
        Path data = tmp.resolve("CreeperCLI");
        Files.createDirectories(data);
        when(plugin.dataFolder()).thenReturn(data);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("AuditLoggerTest"));

        AuditLogger audit = new AuditLogger(plugin);
        audit.start();
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 2000; i++) huge.append("abcdefghij");
        audit.log("127.0.0.1", "admin", "fs.cat", huge.toString());
        Thread.sleep(200);
        audit.stop();

        String content = Files.readString(data.resolve("creepercli-audit.log"));
        assertTrue(content.contains("..."), "long params are truncated");
    }
}