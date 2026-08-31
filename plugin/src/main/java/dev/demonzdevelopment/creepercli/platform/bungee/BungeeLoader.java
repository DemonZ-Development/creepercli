

package dev.demonzdevelopment.creepercli.platform.bungee;

import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.commands.AdminHandler;
import net.md_5.bungee.api.plugin.Plugin;


public final class BungeeLoader extends Plugin {
    private static final int BSTATS_ID = 33129;

    private CreeperCLIPlugin core;

    @Override
    public void onEnable() {
        BungeePlatform platform = new BungeePlatform(this);
        core = new CreeperCLIPlugin(platform, getDescription().getVersion());
        if (!core.start()) {
            getLogger().severe("CreeperCLI failed to start; see log above for the cause");
            return;
        }
        getProxy().getPluginManager().registerCommand(this, new BungeeAdminCommand(new AdminHandler(core)));
        try {
            new org.bstats.bungeecord.Metrics(this, BSTATS_ID);
        } catch (Throwable t) {
            getLogger().warning("bStats init failed: " + t.getMessage());
        }
        getLogger().info("CreeperCLI ready on " + core.platform().software()
                + " - connect with: creepercli login --host <ip> --port " + core.cfg().networkPort());
    }

    @Override
    public void onDisable() {
        if (core != null) core.shutdown();
    }
}
