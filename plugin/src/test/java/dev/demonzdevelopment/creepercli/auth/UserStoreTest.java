

package dev.demonzdevelopment.creepercli.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserStoreTest {

    @TempDir
    Path tempDir;

    private UserStore store;

    @BeforeEach
    void setUp() {
        var plugin = mock(dev.demonzdevelopment.creepercli.CreeperCLIPlugin.class);
        when(plugin.dataFolder()).thenReturn(tempDir);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("UserStoreTest"));
        store = new UserStore(plugin);
    }

    @Test
    void testAddSaveLoadRoundTrip() {
        store.add("steve", "$2a$12$hashhashhash");

        UserStore fresh = newStore();
        User u = fresh.get("steve");
        assertNotNull(u);
        assertEquals("$2a$12$hashhashhash", u.passwordHash());
        assertNull(u.totpSecret());
    }

    @Test
    void testTotpPersistsAndClears() {
        store.add("alex", "$2a$12$x");
        store.setTotp("alex", "SECRET32CHARS000000000000000");

        User reloaded = newStore().get("alex");
        assertNotNull(reloaded);
        assertEquals("SECRET32CHARS000000000000000", reloaded.totpSecret());

        store.setTotp("alex", null);
        assertNull(newStore().get("alex").totpSecret());
    }

    @Test
    void testRemovePersists() {
        store.add("bob", "$2a$12$y");
        store.remove("bob");
        assertNull(newStore().get("bob"));
        assertEquals(0, newStore().count());
    }

    
    @Test
    void testReadsLegacyBukkitFormat() throws IOException {
        Files.writeString(tempDir.resolve("creepercli-users.yml"),
                """
                users:
                  legacy:
                    password: $2a$12$legacyhash
                """);
        UserStore fresh = newStore();
        User u = fresh.get("legacy");
        assertNotNull(u);
        assertEquals("$2a$12$legacyhash", u.passwordHash());
    }

    private UserStore newStore() {
        var plugin = mock(dev.demonzdevelopment.creepercli.CreeperCLIPlugin.class);
        when(plugin.dataFolder()).thenReturn(tempDir);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("UserStoreTest"));
        UserStore fresh = new UserStore(plugin);
        fresh.load();
        return fresh;
    }
}
