

package dev.demonzdevelopment.creepercli.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.CreeperError;
import dev.demonzdevelopment.creepercli.ExecAllowlist;
import dev.demonzdevelopment.creepercli.Json;
import dev.demonzdevelopment.creepercli.Protocol;
import dev.demonzdevelopment.creepercli.net.ClientConnection;

import java.util.concurrent.CompletableFuture;

public final class ExecCommands {
    private final CreeperCLIPlugin plugin;

    public ExecCommands(CreeperCLIPlugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<JsonObject> run(ClientConnection conn, JsonObject params) throws CreeperError {
        String command = Json.opt(params, "command", null);
        if (command == null || command.isBlank()) {
            throw new CreeperError(Protocol.ERR_INVALID_PARAMS, "command is required");
        }
        int timeout = Math.max(1, Math.min(120, Json.optInt(params, "timeout", plugin.cfg().execTimeoutSeconds())));
        ExecAllowlist.Check check = plugin.allowlist().check(command);
        if (!check.allowed()) {
            throw new CreeperError(Protocol.ERR_ALLOWLIST_DENIED,
                    "Command not allowed by the exec allowlist"
                            + (check.matched() == null ? "" : " (closest allowed: " + check.matched() + ")"));
        }
        return plugin.bridge().exec(command, timeout).thenApply(result -> {
            JsonObject data = new JsonObject();
            data.addProperty("success", result.success());
            data.addProperty("elapsedMs", result.elapsedMs());
            JsonArray lines = new JsonArray();
            for (String line : result.lines()) lines.add(line);
            data.add("lines", lines);
            return data;
        });
    }
}
