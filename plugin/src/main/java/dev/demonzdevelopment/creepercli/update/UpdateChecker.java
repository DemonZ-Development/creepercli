
package dev.demonzdevelopment.creepercli.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class UpdateChecker {
    private static final String MODRINTH_PROJECT_ID = "fPKEvBZo";
    private static final String MODRINTH_API_URL = "https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT_ID + "/version";

    private final CreeperCLIPlugin plugin;
    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> task;
    private String latestVersion = null;
    private volatile boolean updateAvailable = false;

    public UpdateChecker(CreeperCLIPlugin plugin, ScheduledExecutorService executor) {
        this.plugin = plugin;
        this.executor = executor;
    }

    public void start() {
        task = executor.scheduleWithFixedDelay(this::checkForUpdates, 5, 4 * 3600L, TimeUnit.SECONDS);
    }

    public void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
    }

    public void checkForUpdates() {
        try {
            URL url = URI.create(MODRINTH_API_URL).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "DemonZDevelopment/CreeperCLI/" + plugin.version() + " (https://modrinth.com/project/creepercli)");

            if (conn.getResponseCode() == 200) {
                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    JsonElement element = JsonParser.parseReader(reader);
                    if (element.isJsonArray()) {
                        JsonArray versions = element.getAsJsonArray();
                        if (versions.size() > 0) {
                            JsonObject latest = versions.get(0).getAsJsonObject();
                            if (latest.has("version_number")) {
                                latestVersion = latest.get("version_number").getAsString();
                                String currentVersion = plugin.version();
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

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String latestVersion() {
        return latestVersion;
    }
}