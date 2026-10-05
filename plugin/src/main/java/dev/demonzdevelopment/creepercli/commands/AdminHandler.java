

package dev.demonzdevelopment.creepercli.commands;

import at.favre.lib.crypto.bcrypt.BCrypt;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.auth.User;
import dev.demonzdevelopment.creepercli.monitor.TpsTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public final class AdminHandler {
    private static final String USAGE = "Usage: /creepercli <status|reload|user|update|help>";

    private final CreeperCLIPlugin plugin;

    public AdminHandler(CreeperCLIPlugin plugin) {
        this.plugin = plugin;
    }

    public List<String> handle(String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 0) {
            out.add(USAGE);
            return out;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> status(out);
            case "reload" -> {
                plugin.reloadConfigs();
                out.add("CreeperCLI configuration reloaded (config version: " + plugin.cfg().configVersion() + ")");
            }
            case "user" -> users(out, args);
            case "update" -> update(out);
            case "help" -> help(out);
            default -> out.add(USAGE);
        }
        return out;
    }

    public List<String> tabComplete(String[] args) {
        if (args.length == 1) {
            return prefixMatch(args[0], List.of("status", "reload", "user", "update", "help"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("user")) {
            return prefixMatch(args[1], List.of("add", "remove", "list"));
        }
        return List.of();
    }

    private static List<String> prefixMatch(String input, List<String> options) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(lower)) matches.add(option);
        }
        return matches;
    }

    private void help(List<String> out) {
        out.add("=== CreeperCLI Slash Commands ===");
        out.add("  /creepercli status     - Show server listener & connection stats");
        out.add("  /creepercli reload     - Reload config.yml & user store");
        out.add("  /creepercli user       - Manage users: add <name> <pass> | remove <name> | list");
        out.add("  /creepercli update     - Check for updates on Modrinth");
    }

    private void status(List<String> out) {
        TpsTracker tps = plugin.tps();
        out.add("CreeperCLI status (" + plugin.platform().software() + " " + plugin.platform().serverVersion() + "):");
        out.add("  Config Version: " + plugin.cfg().configVersion());
        out.add("  Listener: " + plugin.cfg().networkHost() + ":" + plugin.cfg().networkPort());
        out.add("  Connections: " + plugin.server().connectionCount());
        out.add("  Sessions: " + plugin.sessions().count());
        out.add("  Banned IPs: " + plugin.fail2ban().bannedCount());
        out.add("  Edit locks: " + plugin.locks().count());
        out.add("  Log subscribers: " + plugin.logs().subscriberCount());
        out.add("  Users: " + plugin.users().count());
        out.add("  Debug Logging: " + plugin.cfg().debugLog());
        if (tps.running()) {
            out.add("  TPS (1m/5m/15m): " + tps.tps(60) + " / " + tps.tps(300) + " / " + tps.tps(900));
        } else {
            out.add("  TPS (1m/5m/15m): n/a (no tick loop on " + plugin.platform().software() + ")");
        }
        long up = plugin.uptimeMillis() / 1000;
        out.add("  Uptime: " + (up / 86400) + "d " + ((up % 86400) / 3600) + "h " + ((up % 3600) / 60) + "m");
    }

    private void update(List<String> out) {
        out.add("Update check scheduled on Modrinth.");
        if (plugin.updateChecker() != null) {
            plugin.checkForUpdatesAsync();
            if (plugin.updateChecker().isUpdateAvailable()) {
                out.add("Update available: v" + plugin.updateChecker().latestVersion() + " (https://modrinth.com/project/creepercli)");
            } else {
                out.add("Current version: v" + plugin.version() + "; results will be logged when the check completes");
            }
        }
    }

    private void users(List<String> out, String[] args) {
        if (args.length < 2) {
            out.add("Usage: /creepercli user <add <name> <password>|remove <name>|list>");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length < 4) {
                    out.add("Usage: /creepercli user add <name> <password>");
                    return;
                }
                String name = args[2];
                String password = args[3];
                if (password.length() < 8) {
                    out.add("Password must be at least 8 characters");
                    return;
                }
                if (name.equalsIgnoreCase(password)) {
                    out.add("Password must not match the username");
                    return;
                }
                int revoked = plugin.revokeUserSessions(name, null);
                plugin.users().add(name, BCrypt.withDefaults().hashToString(12, password.toCharArray()));
                out.add("User " + name + " added/updated (bcrypt hashed; " + revoked + " session(s) revoked)");
            }
            case "remove" -> {
                if (args.length < 3) {
                    out.add("Usage: /creepercli user remove <name>");
                    return;
                }
                String name = args[2];
                int revoked = plugin.revokeUserSessions(name, null);
                plugin.users().remove(name);
                out.add("User " + name + " removed (" + revoked + " session(s) revoked)");
            }
            case "list" -> {
                for (User u : plugin.users().all()) {
                    out.add("  " + u.username() + (u.totpSecret() != null ? " (totp)" : ""));
                }
                if (plugin.users().count() == 0) {
                    out.add("  (no users)");
                }
            }
            default -> out.add("Usage: /creepercli user <add <name> <password>|remove <name>|list>");
        }
    }
}
