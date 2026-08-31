

package dev.demonzdevelopment.creepercli.platform.bukkit;

import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.platform.ConsoleBridge;
import dev.demonzdevelopment.creepercli.platform.Platform;
import org.bukkit.Bukkit;

import java.nio.file.Path;
import java.util.logging.Logger;


public final class BukkitPlatform implements Platform {
    private final BukkitLoader loader;

    public BukkitPlatform(BukkitLoader loader) {
        this.loader = loader;
    }

    @Override
    public String family() {
        return "bukkit";
    }

    @Override
    public String software() {
        return Bukkit.getName();
    }

    @Override
    public String serverVersion() {
        return Bukkit.getBukkitVersion();
    }

    @Override
    public Logger logger() {
        return Bukkit.getLogger();
    }

    @Override
    public Path dataFolder() {
        return loader.getDataFolder().toPath();
    }

    @Override
    public boolean tickLoopSupported() {
        return true;
    }

    @Override
    public AutoCloseable startTickSampler(Runnable onTick) {
        int taskId = Bukkit.getScheduler()
                .runTaskTimer(loader, onTick, 1L, 1L).getTaskId();
        return () -> Bukkit.getScheduler().cancelTask(taskId);
    }

    @Override
    public ConsoleBridge consoleBridge(CreeperCLIPlugin core) {
        return new BukkitConsoleBridge(loader, core);
    }
}
