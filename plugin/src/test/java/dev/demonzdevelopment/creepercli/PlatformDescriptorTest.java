package dev.demonzdevelopment.creepercli;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformDescriptorTest {
    @Test
    void paperDescriptorPointsToJavaPluginLoader() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/plugin.yml")) {
            Map<?, ?> descriptor = new Yaml().load(in);
            String main = String.valueOf(descriptor.get("main"));
            assertEquals("dev.demonzdevelopment.creepercli.platform.bukkit.BukkitLoader", main);
            assertEquals("1.1.0", String.valueOf(descriptor.get("version")));
            assertTrue(JavaPlugin.class.isAssignableFrom(Class.forName(main)));
        }
    }

    @Test
    void proxyDescriptorsMatchReleaseVersionAndLoaders() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/bungee.yml")) {
            Map<?, ?> descriptor = new Yaml().load(in);
            assertEquals("1.1.0", String.valueOf(descriptor.get("version")));
            assertEquals("dev.demonzdevelopment.creepercli.platform.bungee.BungeeLoader",
                    String.valueOf(descriptor.get("main")));
        }
        try (InputStream in = getClass().getResourceAsStream("/velocity-plugin.json")) {
            JsonObject descriptor = JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject();
            assertEquals("1.1.0", descriptor.get("version").getAsString());
            assertEquals("dev.demonzdevelopment.creepercli.platform.velocity.VelocityLoader",
                    descriptor.get("main").getAsString());
        }
    }
}
