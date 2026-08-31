

package dev.demonzdevelopment.creepercli;

import dev.demonzdevelopment.creepercli.platform.ConsoleBridge;
import dev.demonzdevelopment.creepercli.platform.Platform;

import java.nio.file.Path;
import java.util.logging.Logger;


public final class FakePlatform implements Platform {
    private final Path dataFolder;
    private final Logger logger = Logger.getLogger("FakePlatform");

    public FakePlatform(Path dataFolder) {
        this.dataFolder = dataFolder;
    }

    @Override
    public String family() {
        return "bukkit";
    }

    @Override
    public String software() {
        return "TestServer";
    }

    @Override
    public String serverVersion() {
        return "0.0.0-test";
    }

    @Override
    public Logger logger() {
        return logger;
    }

    @Override
    public Path dataFolder() {
        return dataFolder;
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
        throw new UnsupportedOperationException();
    }
}
