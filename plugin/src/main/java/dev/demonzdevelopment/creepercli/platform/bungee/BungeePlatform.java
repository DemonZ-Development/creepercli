

package dev.demonzdevelopment.creepercli.platform.bungee;

import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.platform.ConsoleBridge;
import dev.demonzdevelopment.creepercli.platform.Platform;
import net.md_5.bungee.api.ProxyServer;

import java.nio.file.Path;
import java.util.logging.Logger;


public final class BungeePlatform implements Platform {
    private final BungeeLoader loader;

    public BungeePlatform(BungeeLoader loader) {
        this.loader = loader;
    }

    @Override
    public String family() {
        return "bungeecord";
    }

    @Override
    public String software() {
        try {
            Class.forName("io.github.waterfallmc.waterfall.conf.WaterfallConfiguration");
            return "Waterfall";
        } catch (ClassNotFoundException | LinkageError ignored) {
            return ProxyServer.getInstance().getName();
        }
    }

    @Override
    public String serverVersion() {
        return ProxyServer.getInstance().getVersion();
    }

    @Override
    public Logger logger() {
        return ProxyServer.getInstance().getLogger();
    }

    @Override
    public Path dataFolder() {
        return loader.getDataFolder().toPath();
    }

    @Override
    public boolean tickLoopSupported() {
        return false;
    }

    @Override
    public AutoCloseable startTickSampler(Runnable onTick) {
        return null;
    }

    @Override
    public ConsoleBridge consoleBridge(CreeperCLIPlugin core) {
        return new BungeeConsoleBridge(core);
    }
}
