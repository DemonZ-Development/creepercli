package dev.demonzdevelopment.creepercli;

import dev.demonzdevelopment.creepercli.auth.AuthLimiter;
import dev.demonzdevelopment.creepercli.auth.AuthManager;
import dev.demonzdevelopment.creepercli.auth.Fail2Ban;
import dev.demonzdevelopment.creepercli.auth.TotpManager;
import dev.demonzdevelopment.creepercli.auth.UserStore;
import dev.demonzdevelopment.creepercli.bukkit.BukkitBridge;
import dev.demonzdevelopment.creepercli.bukkit.ExecAllowlist;
import dev.demonzdevelopment.creepercli.commands.CommandRouter;
import dev.demonzdevelopment.creepercli.monitor.LogStreamer;
import dev.demonzdevelopment.creepercli.monitor.StatsCollector;
import dev.demonzdevelopment.creepercli.monitor.TpsTracker;
import dev.demonzdevelopment.creepercli.net.ClientConnection;
import dev.demonzdevelopment.creepercli.net.SessionManager;
import dev.demonzdevelopment.creepercli.net.TcpServer;
import dev.demonzdevelopment.creepercli.sandbox.PathSanitizer;
import dev.demonzdevelopment.creepercli.security.AuditLogger;
import dev.demonzdevelopment.creepercli.security.FileLockManager;
import dev.demonzdevelopment.creepercli.transfer.TransferManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;

public final class CreeperCLIPlugin extends JavaPlugin {
    private final long startTime = System.currentTimeMillis();

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
    private BukkitBridge bridge;
    private CommandRouter router;
    private TcpServer server;

    @Override
    public void onEnable() {
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
            setEnabled(false);
            return;
        }
        locks = new FileLockManager(cfg);
        transfers = new TransferManager(this);
        tps = new TpsTracker(this);
        tps.start();
        logs = new LogStreamer(this);
        logs.attach();
        stats = new StatsCollector(this);
        allowlist = new ExecAllowlist(cfg);
        bridge = new BukkitBridge(this);
        router = new CommandRouter(this);
        getCommand("creepercli").setExecutor(new AdminCommand(this));
        server = new TcpServer(this);
        try {
            server.start();
        } catch (IOException e) {
            getLogger().severe("Failed to bind TCP " + cfg.networkHost() + ":" + cfg.networkPort() + " - " + e.getMessage());
            setEnabled(false);
            return;
        }
        scheduleSweeps();
        getLogger().info("CreeperCLI enabled. TCP listener on " + cfg.networkHost() + ":" + cfg.networkPort());
    }

    @Override
    public void onDisable() {
        if (server != null) server.stop();
        if (audit != null) audit.stop();
        if (logs != null) logs.detach();
        if (tps != null) tps.stop();
        getLogger().info("CreeperCLI disabled");
    }

    private void scheduleSweeps() {
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            sessions.sweep();
            fail2ban.sweep();
            authManager.purgePending();
        }, 20L * 60, 20L * 60);
        getServer().getScheduler().runTaskTimerAsynchronously(this, locks::sweep, 20L * 30, 20L * 30);
    }

    public void onConnectionClosed(ClientConnection conn) {
        server.remove(conn);
        String token = conn.session() == null ? null : conn.session().token;
        if (token != null) {
            locks.releaseByToken(token);
            transfers.abortByToken(token);
        }
        logs.unsubscribe(conn);
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
        getLogger().info("CreeperCLI configuration reloaded");
    }

    public long uptimeMillis() {
        return System.currentTimeMillis() - startTime;
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

    public BukkitBridge bridge() {
        return bridge;
    }

    public CommandRouter router() {
        return router;
    }

    public TcpServer server() {
        return server;
    }

    public Path serverRoot() {
        return sanitizer.root();
    }
}
