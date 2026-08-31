
package dev.demonzdevelopment.creepercli.platform.bukkit;

import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.auth.User;
import dev.demonzdevelopment.creepercli.commands.AdminHandler;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BukkitLoader extends JavaPlugin {
    private static final int BSTATS_ID = 33129;

    private CreeperCLIPlugin core;
    private Metrics metrics;

    @Override
    public void onEnable() {
        BukkitPlatform platform = new BukkitPlatform(this);
        core = new CreeperCLIPlugin(platform, getDescription().getVersion());
        if (!core.start()) {
            getLogger().severe("CreeperCLI failed to start; see log above for the cause");
            return;
        }
        getCommand("creepercli").setExecutor(new BukkitAdminCommand(this, core.adminHandler()));
        getServer().getPluginManager().registerEvents(new JoinNotifyListener(core), this);
        startMetrics();
    }

    @Override
    public void onDisable() {
        if (metrics != null) metrics.shutdown();
        if (core != null) core.shutdown();
    }

    public CreeperCLIPlugin core() {
        return core;
    }

    private void startMetrics() {
        metrics = new Metrics(this, BSTATS_ID);
        metrics.addCustomChart(new SimplePie("bind_host", () -> core.cfg().networkHost()));
        metrics.addCustomChart(new SimplePie("debug_log", () -> core.cfg().debugLog() ? "enabled" : "disabled"));
        metrics.addCustomChart(new AdvancedPie("two_fa_users", () -> {
            Map<String, Integer> m = new HashMap<>();
            int with = 0;
            int without = 0;
            for (User u : core.users().all()) {
                if (u.totpSecret() == null || u.totpSecret().isEmpty()) without++;
                else with++;
            }
            m.put("with 2FA", with);
            m.put("without 2FA", without);
            return m;
        }));
        metrics.addCustomChart(new SingleLineChart("user_count", core.users()::count));
    }

    private record BukkitAdminCommand(BukkitLoader loader, AdminHandler admin) implements TabExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            loader.getServer().getScheduler().runTaskAsynchronously(loader, () ->
                    admin.handle(args).forEach(sender::sendMessage));
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            return admin.tabComplete(args);
        }
    }

    private record JoinNotifyListener(CreeperCLIPlugin core) implements Listener {
        @EventHandler
        public void onPlayerJoin(PlayerJoinEvent event) {
            if (core.updateChecker() != null
                    && core.updateChecker().isUpdateAvailable()
                    && event.getPlayer().isOp()) {
                event.getPlayer().sendMessage("[CreeperCLI] A new update (v"
                        + core.updateChecker().latestVersion() + ") is available on Modrinth!");
            }
        }
    }
}