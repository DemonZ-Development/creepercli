/*
 * Copyright 2026 DemonZDevelopment
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.demonzdevelopment.creepercli;

import dev.demonzdevelopment.creepercli.api.ActionRegistry;
import dev.demonzdevelopment.creepercli.auth.AuthLimiter;
import dev.demonzdevelopment.creepercli.auth.AuthManager;
import dev.demonzdevelopment.creepercli.auth.Fail2Ban;
import dev.demonzdevelopment.creepercli.auth.TotpManager;
import dev.demonzdevelopment.creepercli.auth.User;
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
import dev.demonzdevelopment.creepercli.update.UpdateChecker;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

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
    private ActionRegistry actionRegistry;
    private TcpServer server;
    private UpdateChecker updateChecker;
    private Metrics metrics;

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
        actionRegistry = new ActionRegistry();
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
        updateChecker = new UpdateChecker(this);
        updateChecker.start();
        startMetrics();
        scheduleSweeps();
        getLogger().info("CreeperCLI enabled (config v" + cfg.configVersion() + "). TCP listener on " + cfg.networkHost() + ":" + cfg.networkPort());
    }

    @Override
    public void onDisable() {
        if (metrics != null) metrics.shutdown();
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

    private void startMetrics() {
        metrics = new Metrics(this, 33129);
        metrics.addCustomChart(new SimplePie("bind_host", () -> cfg.networkHost()));
        metrics.addCustomChart(new SimplePie("debug_log", () -> cfg.debugLog() ? "enabled" : "disabled"));
        metrics.addCustomChart(new AdvancedPie("two_fa_users", () -> {
            Map<String, Integer> m = new HashMap<>();
            int with = 0;
            int without = 0;
            for (User u : users.all()) {
                if (u.totpSecret() == null || u.totpSecret().isEmpty()) without++;
                else with++;
            }
            m.put("with 2FA", with);
            m.put("without 2FA", without);
            return m;
        }));
        metrics.addCustomChart(new SingleLineChart("user_count", users::count));
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
        getLogger().info("CreeperCLI configuration reloaded (v" + cfg.configVersion() + ")");
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
