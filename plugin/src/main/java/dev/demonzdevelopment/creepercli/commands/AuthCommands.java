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
