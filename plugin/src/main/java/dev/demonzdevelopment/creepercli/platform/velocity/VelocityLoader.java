

package dev.demonzdevelopment.creepercli.platform.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.commands.AdminHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;


@Plugin(
        id = "creepercli",
        name = "CreeperCLI",
        version = "1.1.0",
        description = "Remote administration TCP plugin with a hardened file sandbox",
        url = "https://github.com/DemonZ-Development/creepercli",
        authors = {"DemonZDevelopment"}
)
public final class VelocityLoader {
    private static final int BSTATS_ID = 33129;

    private final ProxyServer server;
    private final org.slf4j.Logger slf4jLogger;
    private final Path dataDir;
    private final PluginContainer container;
    private final org.bstats.velocity.Metrics.Factory metricsFactory;

    private CreeperCLIPlugin core;
    private Log4jBridge logBridge;

    @Inject
    public VelocityLoader(ProxyServer server, PluginContainer container, @DataDirectory Path dataDir,
                          org.bstats.velocity.Metrics.Factory metricsFactory) {
        this.server = server;
        this.container = container;
        this.slf4jLogger = LoggerFactory.getLogger("creepercli");
        this.dataDir = dataDir;
        this.metricsFactory = metricsFactory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        String version = container.getDescription().getVersion().orElse("unknown");
        VelocityPlatform platform = new VelocityPlatform(server, dataDir, slf4jLogger);
        core = new CreeperCLIPlugin(platform, version);
        if (!core.start()) {
            slf4jLogger.error("CreeperCLI failed to start; see log above for the cause");
            return;
        }
        VelocityAdminCommand.register(server, this, new AdminHandler(core));
        attachLogBridge();
        try {
            metricsFactory.make(this, BSTATS_ID);
        } catch (Throwable t) {
            slf4jLogger.warn("bStats init failed: {}", t.getMessage());
        }
        slf4jLogger.info("CreeperCLI ready - connect with: creepercli login --host <ip> --port {}", core.cfg().networkPort());
    }

    private void attachLogBridge() {
        try {
            org.apache.logging.log4j.Logger root = LogManager.getRootLogger();
            if (root instanceof Logger coreLogger) {
                logBridge = new Log4jBridge(core.logs());
                logBridge.start();
                coreLogger.addAppender(logBridge);
            }
        } catch (Throwable t) {
            slf4jLogger.warn("Console streaming unavailable (log4j bridge failed): {}", t.getMessage());
        }
    }

    public void detachLogBridge() {
        if (logBridge != null) {
            try {
                org.apache.logging.log4j.Logger root = LogManager.getRootLogger();
                if (root instanceof Logger coreLogger) {
                    coreLogger.removeAppender(logBridge);
                }
                logBridge.stop();
            } catch (Throwable ignored) {
            }
            logBridge = null;
        }
    }

    
    
    @Subscribe
    public void onShutdown(com.velocitypowered.api.event.proxy.ProxyShutdownEvent event) {
        detachLogBridge();
        if (core != null) core.shutdown();
    }
}
