package dev.demonzdevelopment.creepercli;

import org.bukkit.configuration.file.FileConfiguration;

import java.nio.file.Path;
import java.util.List;

public final class PluginConfig {
    private final CreeperCLIPlugin plugin;
    private final FileConfiguration c;

    public PluginConfig(CreeperCLIPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        this.c = plugin.getConfig();
    }

    public String networkHost() {
        return c.getString("network.host", "127.0.0.1");
    }

    public int networkPort() {
        return c.getInt("network.port", 45678);
    }

    public int maxConnections() {
        return c.getInt("network.max-connections", 16);
    }

    public int maxPayloadBytes() {
        return c.getInt("network.max-payload-bytes", 10 * 1024 * 1024);
    }

    public int sessionTimeoutMillis() {
        return c.getInt("auth.session-timeout-minutes", 15) * 60_000;
    }

    public int loginMaxAttempts() {
        return c.getInt("auth.login-limiter.max-attempts", 3);
    }

    public int loginWindowMillis() {
        return c.getInt("auth.login-limiter.window-minutes", 5) * 60_000;
    }

    public int fail2banMaxFailures() {
        return c.getInt("auth.fail2ban.max-failures", 3);
    }

    public int fail2banWindowMillis() {
        return c.getInt("auth.fail2ban.window-minutes", 10) * 60_000;
    }

    public int fail2banBanMillis() {
        return c.getInt("auth.fail2ban.ban-minutes", 10) * 60_000;
    }

    public Path serverRoot() {
        String s = c.getString("sandbox.server-root", "");
        Path root;
        if (s == null || s.isBlank()) {
            Path data = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
            Path plugins = data.getParent();
            root = plugins == null || plugins.getParent() == null ? data : plugins.getParent();
        } else {
            root = Path.of(s);
        }
        return root.toAbsolutePath().normalize();
    }

    public int execTimeoutSeconds() {
        return c.getInt("exec.timeout-seconds", 30);
    }

    public List<String> execAllowlist() {
        return c.getStringList("exec.allowlist");
    }

    public double commandsPerSecond() {
        return c.getDouble("limits.commands-per-second", 10);
    }

    public int fileLockTtlMillis() {
        return c.getInt("limits.file-lock-ttl-minutes", 5) * 60_000;
    }

    public int transferChunkSize() {
        return Math.max(1024, c.getInt("limits.transfer-chunk-size", 65536));
    }

    public long maxTransferBytes() {
        return c.getLong("limits.max-transfer-bytes", 1024L * 1024 * 1024);
    }

    public int logBufferLines() {
        return Math.max(10, c.getInt("monitor.log-buffer-lines", 500));
    }

    public int topRefreshSeconds() {
        return Math.max(1, c.getInt("monitor.top-refresh-seconds", 2));
    }

    public int auditMaxMb() {
        return Math.max(1, c.getInt("audit.max-mb", 10));
    }
}
