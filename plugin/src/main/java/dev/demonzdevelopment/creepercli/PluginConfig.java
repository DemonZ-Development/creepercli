
package dev.demonzdevelopment.creepercli;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PluginConfig {
    public static final int CURRENT_CONFIG_VERSION = 1;

    private final CreeperCLIPlugin plugin;
    private final Path file;
    private Map<String, Object> root;

    public PluginConfig(CreeperCLIPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.dataFolder().resolve("config.yml");
        copyDefaultIfMissing();
        root = loadFile(file);
        migrateIfNeeded();
    }

    public PluginConfig(java.util.List<String> execAllowlist) {
        this.plugin = null;
        this.file = null;
        this.root = new java.util.LinkedHashMap<>();
        java.util.Map<String, Object> exec = new java.util.LinkedHashMap<>();
        exec.put("allowlist", new java.util.ArrayList<>(execAllowlist));
        this.root.put("exec", exec);
    }

    private void copyDefaultIfMissing() {
        try {
            Files.createDirectories(plugin.dataFolder());
            if (Files.notExists(file)) {
                try (InputStream in = PluginConfig.class.getResourceAsStream("/config.yml")) {
                    if (in != null) {
                        Files.copy(in, file);
                    } else {
                        Files.createFile(file);
                        root = new LinkedHashMap<>();
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Cannot create default config: " + e.getMessage());
        }
    }

    private static Map<String, Object> loadFile(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
            if (loaded instanceof Map<?, ?> m) {
                return asStringKeyed(m);
            }
        } catch (IOException | YAMLException e) {
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringKeyed(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    @SuppressWarnings("unchecked")
    private void migrateIfNeeded() {
        int version = getInt("config-version", 0);
        if (version < CURRENT_CONFIG_VERSION) {
            set("config-version", CURRENT_CONFIG_VERSION);
            if (!contains("monitor.debug-log")) set("monitor.debug-log", false);
            if (!contains("sandbox.server-root")) set("sandbox.server-root", ".");
            save();
            if (plugin != null) plugin.getLogger().info("Configuration auto-migrated to version " + CURRENT_CONFIG_VERSION);
        }
    }

    private boolean contains(String path) {
        return leaf(path) != null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parentSection(String[] parts, boolean create) {
        Map<String, Object> current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (!(next instanceof Map)) {
                if (!create) return null;
                Map<String, Object> created = new LinkedHashMap<>();
                current.put(parts[i], created);
                current = created;
            } else {
                current = asStringKeyed((Map<?, ?>) next);
            }
        }
        return current;
    }

    void set(String path, Object value) {
        String[] parts = path.split("\\.");
        if (parts.length == 1) {
            root.put(path, value);
            return;
        }
        Map<String, Object> parent = parentSection(parts, true);
        parent.put(parts[parts.length - 1], value);
    }

    public synchronized void save() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setPrettyFlow(true);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp)) {
                new Yaml(options).dump(root, w);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            if (plugin != null) plugin.getLogger().severe("Failed to save config.yml: " + e.getMessage());
        }
    }

    public int configVersion() {
        return getInt("config-version", 1);
    }

    public boolean debugLog() {
        return getBoolean("monitor.debug-log", false);
    }

    public String networkHost() {
        return getString("network.host", "0.0.0.0");
    }

    public int networkPort() {
        return getInt("network.port", 45678);
    }

    public int maxConnections() {
        return getInt("network.max-connections", 16);
    }

    public int maxPayloadBytes() {
        return getInt("network.max-payload-bytes", 10 * 1024 * 1024);
    }

    public int sessionTimeoutMillis() {
        return getInt("auth.session-timeout-minutes", 15) * 60_000;
    }

    public int loginMaxAttempts() {
        return getInt("auth.login-limiter.max-attempts", 3);
    }

    public int loginWindowMillis() {
        return getInt("auth.login-limiter.window-minutes", 5) * 60_000;
    }

    public int fail2banMaxFailures() {
        return getInt("auth.fail2ban.max-failures", 3);
    }

    public int fail2banWindowMillis() {
        return getInt("auth.fail2ban.window-minutes", 10) * 60_000;
    }

    public int fail2banBanMillis() {
        return getInt("auth.fail2ban.ban-minutes", 10) * 60_000;
    }

    public Path serverRoot() {
        String s = getString("sandbox.server-root", "");
        Path rootPath;
        if (s == null || s.isBlank()) {
            Path data = plugin.dataFolder().toAbsolutePath().normalize();
            Path pluginsDir = data.getParent();
            rootPath = pluginsDir == null || pluginsDir.getParent() == null ? data : pluginsDir.getParent();
        } else {
            rootPath = Path.of(s);
        }
        return rootPath.toAbsolutePath().normalize();
    }

    public int execTimeoutSeconds() {
        return getInt("exec.timeout-seconds", 30);
    }

    public List<String> execAllowlist() {
        List<String> out = new ArrayList<>();
        Object raw = leaf("exec.allowlist");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) out.add(String.valueOf(o));
            }
        }
        return out;
    }

    public double commandsPerSecond() {
        return getDouble("limits.commands-per-second", 30);
    }

    public int fileLockTtlMillis() {
        return getInt("limits.file-lock-ttl-minutes", 5) * 60_000;
    }

    public int transferChunkSize() {
        return Math.max(1024, getInt("limits.transfer-chunk-size", 65536));
    }

    public long maxTransferBytes() {
        return getLong("limits.max-transfer-bytes", 1024L * 1024 * 1024);
    }

    public int logBufferLines() {
        return Math.max(10, getInt("monitor.log-buffer-lines", 500));
    }

    public int topRefreshSeconds() {
        return Math.max(1, getInt("monitor.top-refresh-seconds", 2));
    }

    public int auditMaxMb() {
        return Math.max(1, getInt("audit.max-mb", 10));
    }

    private Object leaf(String path) {
        String[] parts = path.split("\\.");
        if (parts.length == 1) {
            return root.get(path);
        }
        Map<String, Object> section = parentSection(parts, false);
        return section == null ? null : section.get(parts[parts.length - 1]);
    }

    public int getInt(String path, int def) {
        Object v = leaf(path);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public long getLong(String path, long def) {
        Object v = leaf(path);
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public double getDouble(String path, double def) {
        Object v = leaf(path);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public boolean getBoolean(String path, boolean def) {
        Object v = leaf(path);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s.trim());
        return def;
    }

    public String getString(String path, String def) {
        Object v = leaf(path);
        return v == null ? def : String.valueOf(v);
    }
}