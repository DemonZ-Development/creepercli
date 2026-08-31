

package dev.demonzdevelopment.creepercli.platform.velocity;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.commands.AdminHandler;
import net.kyori.adventure.text.Component;

import java.util.List;


final class VelocityAdminCommand implements SimpleCommand {
    private final AdminHandler admin;

    VelocityAdminCommand(AdminHandler admin) {
        this.admin = admin;
    }

    static void register(ProxyServer server, Object pluginRef, AdminHandler admin) {
        CommandManager commandManager = server.getCommandManager();
        CommandMeta meta = commandManager.metaBuilder("creepercli").plugin(pluginRef).build();
        commandManager.register(meta, new VelocityAdminCommand(admin));
    }

    @Override
    public void execute(Invocation invocation) {
        List<String> lines = admin.handle(invocation.arguments());
        invocation.source().sendMessage(Component.text(String.join("\n", lines)));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return isConsole(invocation.source()) || invocation.source().hasPermission("creepercli.admin");
    }

    private static boolean isConsole(com.velocitypowered.api.command.CommandSource source) {
        Class<?> cls = source.getClass();
        while (cls != null) {
            if (cls.getName().equals("com.velocitypowered.api.proxy.ConsoleCommandSource")) return true;
            cls = cls.getSuperclass();
        }
        return false;
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return admin.tabComplete(invocation.arguments());
    }
}
