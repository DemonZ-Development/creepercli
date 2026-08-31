

package dev.demonzdevelopment.creepercli.platform.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.platform.ConsoleBridge;
import dev.demonzdevelopment.creepercli.platform.Platform;

import java.nio.file.Path;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;


public final class VelocityPlatform implements Platform {
    private static volatile Logger julLogger;

    private final ProxyServer server;
    private final Path dataDir;
    private final org.slf4j.Logger slf4j;

    public VelocityPlatform(ProxyServer server, Path dataDir, org.slf4j.Logger slf4j) {
        this.server = server;
        this.dataDir = dataDir;
        this.slf4j = slf4j;
    }

    @Override
    public String family() {
        return "velocity";
    }

    @Override
    public String software() {
        return "Velocity";
    }

    @Override
    public String serverVersion() {
        return server.getVersion().getVersion();
    }

    
    @Override
    public Logger logger() {
        Logger logger = julLogger;
        if (logger == null) {
            synchronized (VelocityPlatform.class) {
                logger = julLogger;
                if (logger == null) {
                    logger = Logger.getLogger("CreeperCLI");
                    logger.setUseParentHandlers(false);
                    logger.addHandler(new Slf4jForwarder(slf4j));
                    julLogger = logger;
                }
            }
        }
        return logger;
    }

    @Override
    public Path dataFolder() {
        return dataDir;
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
        return new VelocityConsoleBridge(server, core);
    }

    
    private static final class Slf4jForwarder extends Handler {
        private final org.slf4j.Logger target;

        Slf4jForwarder(org.slf4j.Logger target) {
            this.target = target;
        }

        @Override
        public void publish(LogRecord record) {
            int intValue = record.getLevel().intValue();
            if (intValue >= Level.SEVERE.intValue()) {
                target.error(record.getMessage(), record.getThrown());
            } else if (intValue >= Level.WARNING.intValue()) {
                target.warn(record.getMessage());
            } else if (intValue >= Level.CONFIG.intValue()) {
                target.debug(record.getMessage());
            } else {
                target.trace(record.getMessage());
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
