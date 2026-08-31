

package dev.demonzdevelopment.creepercli.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.google.gson.JsonObject;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.CreeperError;
import dev.demonzdevelopment.creepercli.Json;
import dev.demonzdevelopment.creepercli.Protocol;
import dev.demonzdevelopment.creepercli.net.ClientConnection;
import dev.demonzdevelopment.creepercli.net.Session;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthManager {
    private record PendingTotp(String username, String secret, long expiresAt) {
    }

    private final CreeperCLIPlugin plugin;
    private final Map<String, PendingTotp> pending = new ConcurrentHashMap<>();

    public AuthManager(CreeperCLIPlugin plugin) {
        this.plugin = plugin;
    }

    public JsonObject login(JsonObject params, ClientConnection conn) throws CreeperError {
        String username = Json.opt(params, "username", null);
        String password = Json.opt(params, "password", null);
        String totpCode = Json.opt(params, "totp", null);
        String ip = conn.remoteIp();
        if (username == null || password == null) {
            throw new CreeperError(Protocol.ERR_INVALID_PARAMS, "username and password are required");
        }
        if (plugin.fail2ban().isBanned(ip)) {
            throw new CreeperError(Protocol.ERR_BANNED,
                    "IP is temporarily banned for " + plugin.fail2ban().banRemainingSeconds(ip) + "s");
        }
        if (!plugin.authLimiter().tryAcquire(ip)) {
            throw new CreeperError(Protocol.ERR_RATE_LIMITED, "Too many login attempts, try again later");
        }
        User user = plugin.users().get(username);
        boolean valid = user != null
                && BCrypt.verifyer().verify(password.toCharArray(), user.passwordHash()).verified;
        if (!valid) {
            plugin.fail2ban().recordFailure(ip);
            plugin.audit().log(ip, username, "auth.login", "failure");
            throw new CreeperError(Protocol.ERR_AUTH_FAILED, "Invalid credentials");
        }
        if (user.totpSecret() != null) {
            if (totpCode == null || !plugin.totp().verify(user.totpSecret(), totpCode)) {
                throw new CreeperError(Protocol.ERR_TOTP_REQUIRED, "TOTP code required");
            }
        }
        plugin.fail2ban().clear(ip);
        Session session = plugin.sessions().create(username, ip);
        plugin.audit().log(ip, username, "auth.login", "success");
        JsonObject res = new JsonObject();
        res.addProperty("token", session.token);
        res.addProperty("username", username);
        res.addProperty("expiresInMinutes", plugin.cfg().sessionTimeoutMillis() / 60_000);
        res.addProperty("totpEnabled", user.totpSecret() != null);
        return res;
    }

    public JsonObject totpSetup(ClientConnection conn, JsonObject params) throws CreeperError {
        Session s = conn.session();
        String password = Json.opt(params, "password", null);
        User user = plugin.users().get(s.username);
        if (user == null) {
            throw new CreeperError(Protocol.ERR_AUTH_FAILED, "User no longer exists");
        }
        if (password == null || !BCrypt.verifyer().verify(password.toCharArray(), user.passwordHash()).verified) {
            throw new CreeperError(Protocol.ERR_AUTH_FAILED, "Invalid password");
        }
        String secret = plugin.totp().generateSecret();
        pending.put(s.username, new PendingTotp(s.username, secret, System.currentTimeMillis() + 10 * 60_000));
        JsonObject res = new JsonObject();
        res.addProperty("secret", secret);
        res.addProperty("otpauthUrl", plugin.totp().otpauthUri(s.username, secret));
        res.addProperty("expiresInSeconds", 600);
        return res;
    }

    public JsonObject totpVerify(ClientConnection conn, JsonObject params) throws CreeperError {
        Session s = conn.session();
        PendingTotp p = pending.get(s.username);
        if (p == null || p.expiresAt() < System.currentTimeMillis()) {
            throw new CreeperError(Protocol.ERR_TOTP_REQUIRED, "TOTP setup expired, restart setup");
        }
        String code = Json.opt(params, "code", null);
        if (!plugin.totp().verify(p.secret(), code)) {
            throw new CreeperError(Protocol.ERR_TOTP_REQUIRED, "Invalid TOTP code");
        }
        plugin.users().setTotp(s.username, p.secret());
        pending.remove(s.username);
        JsonObject res = Json.ok();
        res.addProperty("totpEnabled", true);
        return res;
    }

    public JsonObject totpDisable(ClientConnection conn, JsonObject params) throws CreeperError {
        Session s = conn.session();
        String password = Json.opt(params, "password", null);
        String code = Json.opt(params, "code", null);
        User user = plugin.users().get(s.username);
        if (user == null) {
            throw new CreeperError(Protocol.ERR_AUTH_FAILED, "User no longer exists");
        }
        if (password == null || !BCrypt.verifyer().verify(password.toCharArray(), user.passwordHash()).verified) {
            throw new CreeperError(Protocol.ERR_AUTH_FAILED, "Invalid password");
        }
        if (user.totpSecret() == null) {
            throw new CreeperError(Protocol.ERR_INVALID_PARAMS, "TOTP is not enabled");
        }
        if (!plugin.totp().verify(user.totpSecret(), code)) {
            throw new CreeperError(Protocol.ERR_TOTP_REQUIRED, "Invalid TOTP code");
        }
        plugin.users().setTotp(s.username, null);
        JsonObject res = Json.ok();
        res.addProperty("totpEnabled", false);
        return res;
    }

    public JsonObject changePassword(ClientConnection conn, JsonObject params) throws CreeperError {
        Session s = conn.session();
        String oldPassword = Json.opt(params, "oldPassword", null);
        String newPassword = Json.opt(params, "newPassword", null);
        User user = plugin.users().get(s.username);
        if (user == null) {
            throw new CreeperError(Protocol.ERR_AUTH_FAILED, "User no longer exists");
        }
        if (oldPassword == null || !BCrypt.verifyer().verify(oldPassword.toCharArray(), user.passwordHash()).verified) {
            throw new CreeperError(Protocol.ERR_AUTH_FAILED, "Invalid password");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new CreeperError(Protocol.ERR_INVALID_PARAMS, "New password must be at least 8 characters");
        }
        plugin.users().setPassword(s.username, BCrypt.withDefaults().hashToString(12, newPassword.toCharArray()));
        plugin.sessions().invalidateUserExcept(s.username, s.token);
        plugin.audit().log(s.ip, s.username, "auth.passwd", "changed");
        return Json.ok();
    }

    public void purgePending() {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
    }
}
