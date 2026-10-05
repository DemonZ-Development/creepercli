
package dev.demonzdevelopment.creepercli.auth;

import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class UserStore {
    private final CreeperCLIPlugin plugin;
    private final Path file;
    private final Map<String, User> users = new ConcurrentHashMap<>();

    public UserStore(CreeperCLIPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.dataFolder().resolve("creepercli-users.yml");
    }

    public synchronized void load() {
        users.clear();
        if (Files.notExists(file)) return;
        try (InputStream in = Files.newInputStream(file)) {
            Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
            if (loaded == null) return;
            if (!(loaded instanceof Map<?, ?> root)) {
                if (plugin != null) plugin.getLogger().warning("Users file is not a YAML map; ignoring (existing users cleared)");
                return;
            }
            Object section = root.get("users");
            if (section == null) return;
            if (!(section instanceof Map<?, ?> map)) {
                if (plugin != null) plugin.getLogger().warning("'users' section in users file is not a map; ignoring");
                return;
            }
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String name = String.valueOf(e.getKey());
                if (!(e.getValue() instanceof Map<?, ?> entry)) continue;
                Object pass = entry.get("password");
                if (pass == null) continue;
                Object totp = entry.get("totp");
                String totpSecret = totp == null || String.valueOf(totp).isEmpty() ? null : String.valueOf(totp);
                users.put(name, new User(name, String.valueOf(pass), totpSecret));
            }
        } catch (IOException e) {
            if (plugin != null) plugin.getLogger().severe("Failed to read users file: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public synchronized void save() {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> section = new LinkedHashMap<>();
        for (User u : users.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("password", u.passwordHash());
            entry.put("totp", u.totpSecret());
            section.put(u.username(), entry);
        }
        out.put("users", section);
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp)) {
                new Yaml(options).dump(out, w);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                restrictPermissions(file);
            } catch (IOException e) {
                if (plugin != null) plugin.getLogger().warning("Could not restrict users file permissions: " + e.getMessage());
            }
        } catch (IOException e) {
            if (plugin != null) plugin.getLogger().severe("Failed to save users file: " + e.getMessage());
        }
    }

    private static void restrictPermissions(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Windows ACLs are inherited from the plugin data directory.
        }
    }

    public User get(String username) {
        return users.get(username);
    }

    public void add(String username, String bcryptHash) {
        users.put(username, new User(username, bcryptHash, null));
        save();
    }

    public void remove(String username) {
        users.remove(username);
        save();
    }

    public void setPassword(String username, String bcryptHash) {
        User u = users.get(username);
        if (u != null) {
            users.put(username, new User(username, bcryptHash, u.totpSecret()));
            save();
        }
    }

    public void setTotp(String username, String secret) {
        User u = users.get(username);
        if (u != null) {
            users.put(username, new User(username, u.passwordHash(), secret));
            save();
        }
    }

    public Collection<User> all() {
        return users.values();
    }

    public int count() {
        return users.size();
    }
}
