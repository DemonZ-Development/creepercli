package dev.demonzdevelopment.creepercli;

import at.favre.lib.crypto.bcrypt.BCrypt;
import dev.demonzdevelopment.creepercli.auth.User;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.List;
import java.util.Locale;

public final class AdminCommand implements TabExecutor {
    private final CreeperCLIPlugin plugin;

    public AdminCommand(CreeperCLIPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /creepercli <status|reload|user>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> status(sender);
            case "reload" -> {
                plugin.reloadConfigs();
                sender.sendMessage("CreeperCLI configuration reloaded");
            }
            case "user" -> users(sender, args);
            default -> sender.sendMessage("Usage: /creepercli <status|reload|user>");
        }
        return true;
    }

    private void status(CommandSender sender) {
        sender.sendMessage("CreeperCLI status:");
        sender.sendMessage("  Listener: " + plugin.cfg().networkHost() + ":" + plugin.cfg().networkPort());
        sender.sendMessage("  Connections: " + plugin.server().connectionCount());
        sender.sendMessage("  Sessions: " + plugin.sessions().count());
        sender.sendMessage("  Banned IPs: " + plugin.fail2ban().bannedCount());
        sender.sendMessage("  Edit locks: " + plugin.locks().count());
        sender.sendMessage("  Log subscribers: " + plugin.logs().subscriberCount());
        sender.sendMessage("  Users: " + plugin.users().count());
        sender.sendMessage("  TPS (1m/5m/15m): " + plugin.tps().tps(60) + " / " + plugin.tps().tps(300) + " / " + plugin.tps().tps(900));
        long up = plugin.uptimeMillis() / 1000;
        sender.sendMessage("  Uptime: " + (up / 86400) + "d " + ((up % 86400) / 3600) + "h " + ((up % 3600) / 60) + "m");
    }

    private void users(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /creepercli user <add <name> <password>|remove <name>|list>");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length < 4) {
                    sender.sendMessage("Usage: /creepercli user add <name> <password>");
                    return;
                }
                String name = args[2];
                String password = args[3];
                if (password.length() < 8) {
                    sender.sendMessage("Password must be at least 8 characters");
                    return;
                }
                if (name.equalsIgnoreCase(password)) {
                    sender.sendMessage("Password must not match the username");
                    return;
                }
                plugin.users().add(name, BCrypt.withDefaults().hashToString(12, password.toCharArray()));
                sender.sendMessage("User " + name + " added/updated (bcrypt hashed)");
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage("Usage: /creepercli user remove <name>");
                    return;
                }
                plugin.users().remove(args[2]);
                sender.sendMessage("User " + args[2] + " removed");
            }
            case "list" -> {
                for (User u : plugin.users().all()) {
                    sender.sendMessage("  " + u.username() + (u.totpSecret() != null ? " (totp)" : ""));
                }
                if (plugin.users().count() == 0) {
                    sender.sendMessage("  (no users)");
                }
            }
            default -> sender.sendMessage("Usage: /creepercli user <add <name> <password>|remove <name>|list>");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("status", "reload", "user");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("user")) {
            return List.of("add", "remove", "list");
        }
        return List.of();
    }
}
