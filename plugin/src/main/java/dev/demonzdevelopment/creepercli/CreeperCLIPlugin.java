
package dev.demonzdevelopment.creepercli;

import dev.demonzdevelopment.creepercli.api.ActionRegistry;
import dev.demonzdevelopment.creepercli.auth.AuthLimiter;
import dev.demonzdevelopment.creepercli.auth.AuthManager;
import dev.demonzdevelopment.creepercli.auth.Fail2Ban;
import dev.demonzdevelopment.creepercli.auth.TotpManager;
import dev.demonzdevelopment.creepercli.auth.UserStore;
import dev.demonzdevelopment.creepercli.commands.AdminHandler;
import dev.demonzdevelopment.creepercli.commands.CommandRouter;
import dev.demonzdevelopment.creepercli.monitor.LogStreamer;
import dev.demonzdevelopment.creepercli.monitor.StatsCollector;
import dev.demonzdevelopment.creepercli.monitor.TpsTracker;
import dev.demonzdevelopment.creepercli.net.ClientConnection;
import dev.demonzdevelopment.creepercli.net.SessionManager;
import dev.demonzdevelopment.creepercli.net.TcpServer;
import dev.demonzdevelopment.creepercli.platform.ConsoleBridge;
import dev.demonzdevelopment.creepercli.platform.Platform;
import dev.demonzdevelopment.creepercli.sandbox.PathSanitizer;
import dev.demonzdevelopment.creepercli.security.AuditLogger;
import dev.demonzdevelopment.creepercli.security.FileLockManager;
import dev.demonzdevelopment.creepercli.transfer.TransferManager;
import dev.demonzdevelopment.creepercli.update.UpdateChecker;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class CreeperCLIPlugin {
    private static final String THREAD_PREFIX = "creepercli-";

    private final long startTime = System.currentTimeMillis();
    private final Platform platform;
    private final String pluginVersion;
    private final ScheduledExecutorService executor =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, THREAD_PREFIX + "tasks");
                t.setDaemon(true);
                return t;
            });

    private PluginConfig cfg;
    private UserStore users;
    private TotpManager totp;
    private Fail2Ban fail2ban;
    private AuthLimiter authLimiter;
    private SessionManager sessions;
    private AuthManager authManager;
    private AuditLogger audit;
    private PathSanitizer sanitizer;
    private FileLockManager locks;
    private TransferManager transfers;
    private TpsTracker tps;
    private LogStreamer logs;
    private StatsCollector stats;
    private ExecAllowlist allowlist;
    private ConsoleBridge bridge;
    private CommandRouter router;
    private ActionRegistry actionRegistry;
    private TcpServer server;
    private UpdateChecker updateChecker;
    private AdminHandler adminHandler;
    private boolean started;

    public CreeperCLIPlugin(Platform platform, String pluginVersion) {
        this.platform = platform;
        this.pluginVersion = pluginVersion;
    }

    public boolean start() {
        if (started) return true;
        cfg = new PluginConfig(this);
        users = new UserStore(this);
        users.load();
        totp = new TotpManager();
        fail2ban = new Fail2Ban(cfg);
        authLimiter = new AuthLimiter(cfg);
        sessions = new SessionManager(cfg);
        authManager = new AuthManager(this);
        audit = new AuditLogger(this);
        audit.start();
        try {
            sanitizer = new PathSanitizer(cfg.serverRoot());
        } catch (IOException e) {
            getLogger().severe("Cannot initialize sandbox root: " + e.getMessage());
            shutdown();
            return false;
        }
        locks = new FileLockManager(cfg);
        transfers = new TransferManager(this);
        tps = new TpsTracker(this);
        tps.start();
        logs = new LogStreamer(this);
        logs.attach();
        stats = new StatsCollector(this);
        allowlist = new ExecAllowlist(cfg);
        bridge = platform.consoleBridge(this);
        actionRegistry = new ActionRegistry();
        router = new CommandRouter(this);
        server = new TcpServer(this);
        try {
            server.start();
        } catch (IOException e) {
            getLogger().severe("Failed to bind TCP " + cfg.networkHost() + ":" + cfg.networkPort() + " - " + e.getMessage());
            shutdown();
            return false;
        }
        updateChecker = new UpdateChecker(this, executor);
        updateChecker.start();
        scheduleSweeps();
        adminHandler = new AdminHandler(this);
        started = true;
        getLogger().info("CreeperCLI " + pluginVersion + " enabled on " + platform.software()
                + " " + platform.serverVersion()
                + " (config v" + cfg.configVersion() + "). TCP listener on "
                + cfg.networkHost() + ":" + cfg.networkPort());
        return true;
    }

    public void shutdown() {
        if (updateChecker != null) updateChecker.stop();
        executor.shutdownNow();
        if (server != null) server.stop();
        if (audit != null) audit.stop();
        if (logs != null) logs.detach();
        if (tps != null) tps.stop();
        started = false;
        if (getLogger() != null) getLogger().info("CreeperCLI disabled");
    }

    private void scheduleSweeps() {
        executor.scheduleAtFixedRate(() -> {
            sessions.sweep();
            fail2ban.sweep();
            authLimiter.sweep();
            authManager.purgePending();
            transfers.sweep();
        }, 60, 60, TimeUnit.SECONDS);
        executor.scheduleAtFixedRate(locks::sweep, 30, 30, TimeUnit.SECONDS);
    }

    public void onConnectionClosed(ClientConnection conn) {
        if (server != null) server.remove(conn);
        String token = conn.session() == null ? null : conn.session().token;
        if (token != null) {
            locks.releaseByToken(token);
            transfers.abortByToken(token);
        }
        logs.unsubscribe(conn);
    }

    public int revokeUserSessions(String username, String exceptToken) {
        int revoked = sessions.invalidateUserExcept(username, exceptToken);
        if (server != null) server.disconnectUserExcept(username, exceptToken);
        return revoked;
    }

    public void checkForUpdatesAsync() {
        if (updateChecker != null && !executor.isShutdown()) {
            executor.execute(updateChecker::checkForUpdates);
        }
    }

    public void reloadConfigs() {
        cfg = new PluginConfig(this);
        users.load();
        fail2ban = new Fail2Ban(cfg);
        authLimiter = new AuthLimiter(cfg);
        sessions = new SessionManager(cfg);
        allowlist = new ExecAllowlist(cfg);
        try {
            sanitizer = new PathSanitizer(cfg.serverRoot());
        } catch (IOException e) {
            getLogger().severe("Failed to reinitialize sandbox root: " + e.getMessage());
        }
        if (locks != null) locks.sweepAll();
        if (transfers != null) transfers.abortAll();
        if (logs != null) logs.unsubscribeAll();
        getLogger().info("CreeperCLI configuration reloaded (v" + cfg.configVersion() + ")");
    }

    public AdminHandler adminHandler() {
        return adminHandler;
    }

    public long uptimeMillis() {
        return System.currentTimeMillis() - startTime;
    }

    public Platform platform() {
        return platform;
    }

    public String version() {
        return pluginVersion;
    }

    public java.util.logging.Logger getLogger() {
        return platform == null ? null : platform.logger();
    }

    public Path dataFolder() {
        return platform.dataFolder();
    }

    public PluginConfig cfg() {
        return cfg;
    }

    public UserStore users() {
        return users;
    }

    public TotpManager totp() {
        return totp;
    }

    public Fail2Ban fail2ban() {
        return fail2ban;
    }

    public AuthLimiter authLimiter() {
        return authLimiter;
    }

    public SessionManager sessions() {
        return sessions;
    }

    public AuthManager authManager() {
        return authManager;
    }

    public AuditLogger audit() {
        return audit;
    }

    public PathSanitizer sanitizer() {
        return sanitizer;
    }

    public FileLockManager locks() {
        return locks;
    }

    public TransferManager transfers() {
        return transfers;
    }

    public TpsTracker tps() {
        return tps;
    }

    public LogStreamer logs() {
        return logs;
    }

    public StatsCollector stats() {
        return stats;
    }

    public ExecAllowlist allowlist() {
        return allowlist;
    }

    public ConsoleBridge bridge() {
        return bridge;
    }

    public CommandRouter router() {
        return router;
    }

    public ActionRegistry actionRegistry() {
        return actionRegistry;
    }

    public TcpServer server() {
        return server;
    }

    public UpdateChecker updateChecker() {
        return updateChecker;
    }

    public Path serverRoot() {
        return sanitizer.root();
    }
}
