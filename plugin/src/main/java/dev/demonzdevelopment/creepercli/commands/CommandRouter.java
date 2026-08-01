package dev.demonzdevelopment.creepercli.commands;

import com.google.gson.JsonObject;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.CreeperError;
import dev.demonzdevelopment.creepercli.Json;
import dev.demonzdevelopment.creepercli.Protocol;
import dev.demonzdevelopment.creepercli.net.ClientConnection;
import dev.demonzdevelopment.creepercli.net.Session;

import java.util.concurrent.CompletableFuture;

public final class CommandRouter {
    private final CreeperCLIPlugin plugin;
    private final FsCommands fsCommands;
    private final EditCommands editCommands;
    private final SearchCommands searchCommands;
    private final TransferCommands transferCommands;
    private final ExecCommands execCommands;
    private final MonitorCommands monitorCommands;
    private final AuthCommands authCommands;

    public CommandRouter(CreeperCLIPlugin plugin) {
        this.plugin = plugin;
        this.fsCommands = new FsCommands(plugin);
        this.editCommands = new EditCommands(plugin);
        this.searchCommands = new SearchCommands(plugin);
        this.transferCommands = new TransferCommands(plugin);
        this.execCommands = new ExecCommands(plugin);
        this.monitorCommands = new MonitorCommands(plugin);
        this.authCommands = new AuthCommands(plugin);
    }

    public CompletableFuture<JsonObject> route(ClientConnection conn, String action, JsonObject params) throws CreeperError {
        switch (action) {
            case Protocol.ACTION_PING -> {
                JsonObject data = new JsonObject();
                data.addProperty("pong", true);
                data.addProperty("protocol", Protocol.VERSION);
                data.addProperty("serverTime", System.currentTimeMillis());
                data.addProperty("pluginVersion", plugin.getPluginMeta().getVersion());
                return done(data);
            }
            case Protocol.ACTION_AUTH_LOGIN -> {
                JsonObject res = plugin.authManager().login(params, conn);
                String token = Json.opt(res, "token", null);
                if (token != null) {
                    conn.setSession(plugin.sessions().getValid(token, conn.remoteIp()));
                }
                return done(res);
            }
            case Protocol.ACTION_AUTH_LOGOUT -> {
                String token = Json.opt(params, "token", null);
                Session s = conn.session();
                if (token != null) {
                    Session target = plugin.sessions().getValid(token, conn.remoteIp());
                    if (target != null) {
                        plugin.sessions().invalidate(target.token);
                        plugin.audit().log(target.ip, target.username, "auth.logout", "token");
                    }
                } else if (s != null) {
                    plugin.sessions().invalidate(s.token);
                    plugin.audit().log(s.ip, s.username, "auth.logout", "-");
                }
                conn.setSession(null);
                return done(Json.ok());
            }
            default -> {
                Session s = requireSession(conn);
                Session valid = plugin.sessions().getValid(s.token, conn.remoteIp());
                if (valid == null) {
                    conn.setSession(null);
                    throw new CreeperError(Protocol.ERR_SESSION_EXPIRED, "Session expired, please login again");
                }
                conn.setSession(valid);
                if (!valid.limiter.tryAcquire()) {
                    throw new CreeperError(Protocol.ERR_RATE_LIMITED, "Command rate limit exceeded");
                }
                plugin.audit().log(valid.ip, valid.username, action, params.toString());
                return dispatch(action, params, conn);
            }
        }
    }

    private CompletableFuture<JsonObject> dispatch(String action, JsonObject params, ClientConnection conn) throws CreeperError {
        try {
            return switch (action) {
                case Protocol.ACTION_AUTH_WHOAMI -> done(authCommands.whoami(conn));
                case Protocol.ACTION_AUTH_PASSWD -> done(plugin.authManager().changePassword(conn, params));
                case Protocol.ACTION_AUTH_TOTP_SETUP -> done(plugin.authManager().totpSetup(conn, params));
                case Protocol.ACTION_AUTH_TOTP_VERIFY -> done(plugin.authManager().totpVerify(conn, params));
                case Protocol.ACTION_AUTH_TOTP_DISABLE -> done(plugin.authManager().totpDisable(conn, params));

                case Protocol.ACTION_FS_PWD -> done(fsCommands.pwd(conn));
                case Protocol.ACTION_FS_LS -> done(fsCommands.ls(conn, params));
                case Protocol.ACTION_FS_CD -> done(fsCommands.cd(conn, params));
                case Protocol.ACTION_FS_TREE -> done(fsCommands.tree(conn, params));
                case Protocol.ACTION_FS_CAT -> done(fsCommands.cat(conn, params));
                case Protocol.ACTION_FS_HEAD -> done(fsCommands.head(conn, params));
                case Protocol.ACTION_FS_TAIL -> done(fsCommands.tail(conn, params));
                case Protocol.ACTION_FS_WC -> done(fsCommands.wc(conn, params));
                case Protocol.ACTION_FS_TOUCH -> done(fsCommands.touch(conn, params));
                case Protocol.ACTION_FS_MKDIR -> done(fsCommands.mkdir(conn, params));
                case Protocol.ACTION_FS_RM -> done(fsCommands.rm(conn, params));
                case Protocol.ACTION_FS_CP -> done(fsCommands.cp(conn, params));
                case Protocol.ACTION_FS_MV -> done(fsCommands.mv(conn, params));
                case Protocol.ACTION_FS_INFO -> done(fsCommands.info(conn, params));

                case Protocol.ACTION_FS_EDIT_LOCK -> done(editCommands.lock(conn, params));
                case Protocol.ACTION_FS_EDIT_PUSH -> done(editCommands.push(conn, params));
                case Protocol.ACTION_FS_EDIT_UNLOCK -> done(editCommands.unlock(conn, params));

                case Protocol.ACTION_FS_GREP -> done(searchCommands.grep(conn, params));
                case Protocol.ACTION_FS_FIND -> done(searchCommands.find(conn, params));

                case Protocol.ACTION_XFER_PUSH_START -> done(transferCommands.pushStart(conn, params));
                case Protocol.ACTION_XFER_PUSH_CHUNK -> done(transferCommands.pushChunk(conn, params));
                case Protocol.ACTION_XFER_PUSH_FINISH -> done(transferCommands.pushFinish(conn, params));
                case Protocol.ACTION_XFER_PULL_START -> done(transferCommands.pullStart(conn, params));
                case Protocol.ACTION_XFER_PULL_CHUNK -> done(transferCommands.pullChunk(conn, params));
                case Protocol.ACTION_XFER_PULL_FINISH -> done(transferCommands.pullFinish(conn, params));
                case Protocol.ACTION_XFER_ABORT -> done(transferCommands.abort(conn, params));
                case Protocol.ACTION_XFER_LIST -> done(transferCommands.list(conn, params));

                case Protocol.ACTION_EXEC_RUN -> execCommands.run(conn, params);

                case Protocol.ACTION_MONITOR_STATS -> done(monitorCommands.stats(conn));
                case Protocol.ACTION_MONITOR_TPS -> done(monitorCommands.tps(conn));
                case Protocol.ACTION_MONITOR_TOP -> done(monitorCommands.top(conn));
                case Protocol.ACTION_MONITOR_LOG_START -> done(monitorCommands.logStart(conn, params));
                case Protocol.ACTION_MONITOR_LOG_STOP -> done(monitorCommands.logStop(conn));

                default -> throw new CreeperError(Protocol.ERR_BAD_REQUEST, "Unknown action: " + action);
            };
        } catch (CreeperError e) {
            throw e;
        } catch (Exception e) {
            plugin.getLogger().warning("Handler error for " + action + ": " + e);
            throw new CreeperError(Protocol.ERR_INTERNAL, "Internal server error");
        }
    }

    private Session requireSession(ClientConnection conn) throws CreeperError {
        Session s = conn.session();
        if (s == null) {
            throw new CreeperError(Protocol.ERR_UNAUTHORIZED, "Not authenticated");
        }
        return s;
    }

    private static CompletableFuture<JsonObject> done(JsonObject data) {
        return CompletableFuture.completedFuture(data);
    }
}
