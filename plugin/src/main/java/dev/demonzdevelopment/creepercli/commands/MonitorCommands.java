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
