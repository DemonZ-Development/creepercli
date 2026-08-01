package dev.demonzdevelopment.creepercli.auth;

import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class UserStore {
    private final CreeperCLIPlugin plugin;
    private final File file;
    private final Map<String, User> users = new ConcurrentHashMap<>();

    public UserStore(CreeperCLIPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "creepercli-users.yml");
    }

    public synchronized void load() {
        users.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("users");
        if (section == null) return;
        for (String name : section.getKeys(false)) {
            String pass = section.getString(name + ".password");
            String totp = section.getString(name + ".totp");
            if (pass != null) {
                users.put(name, new User(name, pass, totp == null || totp.isEmpty() ? null : totp));
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (User u : users.values()) {
            yaml.set("users." + u.username() + ".password", u.passwordHash());
            yaml.set("users." + u.username() + ".totp", u.totpSecret());
        }
        try {
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            yaml.save(tmp);
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save users file: " + e.getMessage());
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
