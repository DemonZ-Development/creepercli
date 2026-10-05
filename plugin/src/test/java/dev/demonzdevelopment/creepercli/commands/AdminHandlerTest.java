

package dev.demonzdevelopment.creepercli.commands;

import at.favre.lib.crypto.bcrypt.BCrypt;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.auth.User;
import dev.demonzdevelopment.creepercli.auth.UserStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminHandlerTest {

    private CreeperCLIPlugin plugin;
    private UserStore users;
    private AdminHandler handler;

    @BeforeEach
    void setUp() {
        plugin = mock(CreeperCLIPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("AdminHandlerTest"));
        users = mock(UserStore.class);
        when(plugin.users()).thenReturn(users);
        handler = new AdminHandler(plugin);
    }

    @Test
    void testUsageWhenNoArgs() {
        List<String> out = handler.handle(new String[0]);
        assertEquals(1, out.size());
        assertTrue(out.get(0).contains("Usage: /creepercli"));
    }

    @Test
    void testHelpListsSubcommands() {
        List<String> out = handler.handle(new String[]{"help"});
        assertTrue(out.stream().anyMatch(l -> l.contains("status")));
        assertTrue(out.stream().anyMatch(l -> l.contains("user")));
    }

    @Test
    void testUserAddRejectsShortPassword() {
        List<String> out = handler.handle(new String[]{"user", "add", "steve", "short"});
        assertTrue(out.get(0).contains("at least 8 characters"));
        verify(users, never()).add(anyString(), anyString());
    }

    @Test
    void testUserAddRejectsUsernameMatchingPassword() {
        List<String> out = handler.handle(new String[]{"user", "add", "administrator", "ADMINISTRATOR"});
        assertTrue(out.get(0).contains("must not match the username"));
        verify(users, never()).add(anyString(), anyString());
    }

    @Test
    void testUserAddStoresBcryptHash() {
        List<String> out = handler.handle(new String[]{"user", "add", "steve", "correct-horse-battery"});
        assertTrue(out.get(0).contains("added/updated"));
        verify(plugin).revokeUserSessions("steve", null);
        verify(users).add(eq("steve"), argThat(hash ->
                hash.startsWith("$2") && BCrypt.verifyer().verify("correct-horse-battery".toCharArray(), hash).verified));
    }

    @Test
    void testUserRemove() {
        List<String> out = handler.handle(new String[]{"user", "remove", "steve"});
        assertTrue(out.get(0).contains("removed"));
        verify(plugin).revokeUserSessions("steve", null);
        verify(users).remove("steve");
    }

    @Test
    void testUserListEmpty() {
        when(users.count()).thenReturn(0);
        List<String> out = handler.handle(new String[]{"user", "list"});
        assertTrue(out.contains("  (no users)"));
    }

    @Test
    void testUserListShowsTotpFlag() {
        when(users.all()).thenReturn(List.of(new User("alex", "$2a$hash", "SECRET")));
        when(users.count()).thenReturn(1);
        List<String> out = handler.handle(new String[]{"user", "list"});
        assertEquals(1, out.size());
        assertEquals("  alex (totp)", out.get(0));
    }

    @Test
    void testTabCompleteTopLevelAndUser() {
        assertEquals(List.of("status"), handler.tabComplete(new String[]{"s"}));
        assertEquals(List.of("list"), handler.tabComplete(new String[]{"user", "l"}));
        assertTrue(handler.tabComplete(new String[]{"user", "add", "x"}).isEmpty());
    }
}
