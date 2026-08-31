

package dev.demonzdevelopment.creepercli.commands;

import com.google.gson.JsonObject;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.Json;
import dev.demonzdevelopment.creepercli.net.ClientConnection;
import dev.demonzdevelopment.creepercli.net.Session;

public final class AuthCommands {
    private final CreeperCLIPlugin plugin;

    public AuthCommands(CreeperCLIPlugin plugin) {
        this.plugin = plugin;
    }

    public JsonObject whoami(ClientConnection conn) {
        Session s = conn.session();
        JsonObject res = new JsonObject();
        res.addProperty("username", s.username);
        res.addProperty("ip", s.ip);
        res.addProperty("cwd", Session.cwdString(s));
        res.addProperty("expiresInSeconds", plugin.sessions().remainingMillis(s.token) / 1000);
        res.addProperty("totpEnabled", isTotpEnabled(s.username));
        return res;
    }

    private boolean isTotpEnabled(String username) {
        var user = plugin.users().get(username);
        return user != null && user.totpSecret() != null;
    }
}
