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

package dev.demonzdevelopment.creepercli.api;

import com.google.gson.JsonObject;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.CreeperError;
import dev.demonzdevelopment.creepercli.Json;
import dev.demonzdevelopment.creepercli.Protocol;
import dev.demonzdevelopment.creepercli.commands.CommandRouter;
import dev.demonzdevelopment.creepercli.net.ClientConnection;
import dev.demonzdevelopment.creepercli.net.Session;
import dev.demonzdevelopment.creepercli.net.SessionManager;
import dev.demonzdevelopment.creepercli.security.AuditLogger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActionRegistryTest {

    private ActionRegistry registry;
    private CreeperCLIPlugin plugin;
    private SessionManager sessionManager;
    private AuditLogger auditLogger;
    private CommandRouter router;
    private ClientConnection connection;

    @BeforeEach
    void setUp() {
        registry = new ActionRegistry();
        plugin = mock(CreeperCLIPlugin.class);
        sessionManager = mock(SessionManager.class);
        auditLogger = mock(AuditLogger.class);
        connection = mock(ClientConnection.class);

        when(plugin.actionRegistry()).thenReturn(registry);
        when(plugin.sessions()).thenReturn(sessionManager);
        when(plugin.audit()).thenReturn(auditLogger);

        router = new CommandRouter(plugin);
    }

    @Test
    void testRegisterAndRetrieveAction() {
        ActionHandler handler = (conn, params) -> CompletableFuture.completedFuture(Json.ok());
        registry.registerAction("custom.test_action", handler);

        assertTrue(registry.hasAction("custom.test_action"));
        assertEquals(handler, registry.getHandler("custom.test_action"));
        assertEquals(1, registry.registeredActions().size());
        assertTrue(registry.registeredActions().contains("custom.test_action"));
    }

    @Test
    void testRegisterValidation() {
        ActionHandler handler = (conn, params) -> CompletableFuture.completedFuture(Json.ok());
        assertThrows(IllegalArgumentException.class, () -> registry.registerAction("", handler));
        assertThrows(IllegalArgumentException.class, () -> registry.registerAction("   ", handler));
        assertThrows(IllegalArgumentException.class, () -> registry.registerAction(null, handler));
        assertThrows(IllegalArgumentException.class, () -> registry.registerAction("valid.name", null));
    }

    @Test
    void testUnregisterAction() {
        ActionHandler handler = (conn, params) -> CompletableFuture.completedFuture(Json.ok());
        registry.registerAction("custom.remove_me", handler);
        assertTrue(registry.hasAction("custom.remove_me"));

        assertTrue(registry.unregisterAction("custom.remove_me"));
        assertFalse(registry.hasAction("custom.remove_me"));
        assertFalse(registry.unregisterAction("custom.remove_me"));
    }

    @Test
    void testClearRegistry() {
        registry.registerAction("custom.action1", (conn, params) -> CompletableFuture.completedFuture(Json.ok()));
        registry.registerAction("custom.action2", (conn, params) -> CompletableFuture.completedFuture(Json.ok()));
        assertEquals(2, registry.registeredActions().size());

        registry.clear();
        assertEquals(0, registry.registeredActions().size());
    }

    @Test
    void testCustomActionRoutedSuccessfully() throws Exception {
        String token = "valid_token";
        String ip = "127.0.0.1";
        dev.demonzdevelopment.creepercli.security.RateLimiter rateLimiter = mock(dev.demonzdevelopment.creepercli.security.RateLimiter.class);
        when(rateLimiter.tryAcquire()).thenReturn(true);
        Session s = new Session(token, "admin", ip, rateLimiter);

        when(connection.session()).thenReturn(s);
        when(connection.remoteIp()).thenReturn(ip);
        when(sessionManager.getValid(token, ip)).thenReturn(s);

        ActionHandler customHandler = (conn, params) -> {
            JsonObject res = Json.ok();
            res.addProperty("customData", "hello_world");
            return CompletableFuture.completedFuture(res);
        };

        registry.registerAction("thirdparty.custom_greet", customHandler);

        CompletableFuture<JsonObject> future = router.route(connection, "thirdparty.custom_greet", new JsonObject());
        JsonObject response = future.get();

        assertNotNull(response);
        assertEquals("hello_world", response.get("customData").getAsString());
        verify(auditLogger).log(eq(ip), eq("admin"), eq("thirdparty.custom_greet"), anyString());
    }
}
