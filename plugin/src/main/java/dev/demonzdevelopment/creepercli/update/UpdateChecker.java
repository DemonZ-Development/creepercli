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

package dev.demonzdevelopment.creepercli.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class UpdateChecker implements Listener {
    private static final String MODRINTH_PROJECT_ID = "fPKEvBZo";
    private static final String MODRINTH_API_URL = "https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT_ID + "/version";

    private final CreeperCLIPlugin plugin;
    private String latestVersion = null;
    private boolean updateAvailable = false;

    public UpdateChecker(CreeperCLIPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // Run initial check 5 seconds after startup, then silently every 4 hours (20 ticks * 3600 sec * 4)
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::checkForUpdates, 20L * 5, 20L * 60 * 60 * 4);
    }

    public void checkForUpdates() {
        try {
            URL url = URI.create(MODRINTH_API_URL).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "DemonZDevelopment/CreeperCLI/" + plugin.getPluginMeta().getVersion() + " (https://modrinth.com/project/creepercli)");

            if (conn.getResponseCode() == 200) {
                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    JsonElement element = JsonParser.parseReader(reader);
                    if (element.isJsonArray()) {
                        JsonArray versions = element.getAsJsonArray();
                        if (versions.size() > 0) {
                            JsonObject latest = versions.get(0).getAsJsonObject();
                            if (latest.has("version_number")) {
                                latestVersion = latest.get("version_number").getAsString();
                                String currentVersion = plugin.getPluginMeta().getVersion();
                                if (isNewerVersion(currentVersion, latestVersion)) {
                                    updateAvailable = true;
                                    plugin.getLogger().info("[UpdateChecker] A new version of CreeperCLI (v" + latestVersion + ") is available on Modrinth!");
                                    plugin.getLogger().info("[UpdateChecker] Download at: https://modrinth.com/project/creepercli");
                                } else {
                                    updateAvailable = false;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (plugin.cfg().debugLog()) {
                plugin.getLogger().warning("[UpdateChecker] Failed to check for updates on Modrinth: " + e.getMessage());
            }
        }
    }

    private boolean isNewerVersion(String current, String latest) {
        if (current == null || latest == null) return false;
        String[] c = current.split("-")[0].split("\\.");
        String[] l = latest.split("-")[0].split("\\.");
        int max = Math.max(c.length, l.length);
        for (int i = 0; i < max; i++) {
            int cv = i < c.length ? parseOrZero(c[i]) : 0;
            int lv = i < l.length ? parseOrZero(l[i]) : 0;
            if (lv > cv) return true;
            if (lv < cv) return false;
        }
        return false;
    }

    private int parseOrZero(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (updateAvailable && event.getPlayer().isOp()) {
            event.getPlayer().sendMessage("[CreeperCLI] A new update (v" + latestVersion + ") is available on Modrinth!");
        }
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String latestVersion() {
        return latestVersion;
    }
}
