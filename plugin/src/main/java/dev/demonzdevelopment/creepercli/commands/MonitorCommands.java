package dev.demonzdevelopment.creepercli.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.CreeperError;
import dev.demonzdevelopment.creepercli.Json;
import dev.demonzdevelopment.creepercli.Protocol;
import dev.demonzdevelopment.creepercli.net.ClientConnection;

public final class MonitorCommands {
    private final CreeperCLIPlugin plugin;

    public MonitorCommands(CreeperCLIPlugin plugin) {
        this.plugin = plugin;
    }

    public JsonObject stats(ClientConnection conn) {
        return plugin.stats().collect();
    }

    public JsonObject tps(ClientConnection conn) {
        return plugin.tps().snapshot();
    }

    public JsonObject top(ClientConnection conn) {
        JsonObject res = new JsonObject();
        res.add("stats", stats(conn));
        res.add("tps", tps(conn));
        return res;
    }

    public JsonObject logStart(ClientConnection conn, JsonObject params) throws CreeperError {
        String grep = Json.opt(params, "grep", null);
        plugin.logs().subscribe(conn, grep);
        JsonObject res = new JsonObject();
        res.addProperty("grep", grep);
        JsonArray buffer = new JsonArray();
        for (String line : plugin.logs().buffer()) buffer.add(line);
        res.add("buffer", buffer);
        res.addProperty("subscribers", plugin.logs().subscriberCount());
        return res;
    }

    public JsonObject logStop(ClientConnection conn) {
        plugin.logs().unsubscribe(conn);
        JsonObject res = Json.ok();
        res.addProperty("subscribers", plugin.logs().subscriberCount());
        return res;
    }
}
