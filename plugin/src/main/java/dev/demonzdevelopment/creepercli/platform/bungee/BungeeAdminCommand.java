

package dev.demonzdevelopment.creepercli.platform.bungee;

import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.commands.AdminHandler;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

import java.util.List;


final class BungeeAdminCommand extends Command implements TabExecutor {
    private final AdminHandler admin;

    BungeeAdminCommand(AdminHandler admin) {
        super("creepercli");
        this.admin = admin;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return isConsole(sender) || sender.hasPermission("creepercli.admin");
    }

    private static boolean isConsole(CommandSender sender) {
        Class<?> cls = sender.getClass();
        while (cls != null) {
            if (cls.getName().equals("net.md_5.bungee.command.ConsoleCommandSender")) return true;
            cls = cls.getSuperclass();
        }
        return false;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        List<String> lines = admin.handle(args);
        sender.sendMessage(TextComponent.fromLegacyText(String.join("\n", lines)));
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        return admin.tabComplete(args);
    }
}
