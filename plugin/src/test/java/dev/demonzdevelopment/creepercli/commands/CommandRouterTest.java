

package dev.demonzdevelopment.creepercli.commands;

import com.google.gson.JsonObject;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.CreeperError;
import dev.demonzdevelopment.creepercli.PluginConfig;
import dev.demonzdevelopment.creepercli.Protocol;
import dev.demonzdevelopment.creepercli.api.ActionRegistry;
import dev.demonzdevelopment.creepercli.auth.AuthLimiter;
import dev.demonzdevelopment.creepercli.auth.AuthManager;
import dev.demonzdevelopment.creepercli.auth.Fail2Ban;
import dev.demonzdevelopment.creepercli.auth.UserStore;
import dev.demonzdevelopment.creepercli.net.ClientConnection;
import dev.demonzdevelopment.creepercli.net.Session;
import dev.demonzdevelopment.creepercli.net.SessionManager;
import dev.demonzdevelopment.creepercli.sandbox.PathSanitizer;
import dev.demonzdevelopment.creepercli.security.AuditLogger;
import dev.demonzdevelopment.creepercli.security.FileLockManager;
import dev.demonzdevelopment.creepercli.security.RateLimiter;
import dev.demonzdevelopment.creepercli.transfer.TransferManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CommandRouterTest {

    @TempDir
    Path tempDir;

    private CreeperCLIPlugin plugin;
    private PluginConfig cfg;
    private SessionManager sessionManager;
    private AuthManager authManager;
    private AuditLogger auditLogger;
    private PathSanitizer sanitizer;
    private FileLockManager fileLockManager;
    private TransferManager transferManager;
    private CommandRouter router;
    private ClientConnection conn;
    private Session validSession;

    @BeforeEach
    void setUp() throws IOException {
        plugin = mock(CreeperCLIPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("CreeperCLIPluginTest"));

        cfg = mock(PluginConfig.class);
        sessionManager = mock(SessionManager.class);
        authManager = mock(AuthManager.class);
        auditLogger = mock(AuditLogger.class);
        sanitizer = new PathSanitizer(tempDir);
        fileLockManager = mock(FileLockManager.class);
        transferManager = mock(TransferManager.class);
        UserStore userStore = mock(UserStore.class);
        Fail2Ban fail2ban = mock(Fail2Ban.class);
        AuthLimiter authLimiter = mock(AuthLimiter.class);

        when(plugin.version()).thenReturn("1.0.0");

        when(plugin.cfg()).thenReturn(cfg);
        when(plugin.sessions()).thenReturn(sessionManager);
        when(plugin.authManager()).thenReturn(authManager);
        when(plugin.audit()).thenReturn(auditLogger);
        when(plugin.sanitizer()).thenReturn(sanitizer);
        when(plugin.locks()).thenReturn(fileLockManager);
        when(plugin.transfers()).thenReturn(transferManager);
        ActionRegistry actionRegistry = new ActionRegistry();
        when(plugin.actionRegistry()).thenReturn(actionRegistry);
        when(plugin.users()).thenReturn(userStore);
        when(plugin.fail2ban()).thenReturn(fail2ban);
        when(plugin.authLimiter()).thenReturn(authLimiter);

        router = new CommandRouter(plugin);

        conn = mock(ClientConnection.class);
        when(conn.remoteIp()).thenReturn("127.0.0.1");

        RateLimiter rateLimiter = mock(RateLimiter.class);
        when(rateLimiter.tryAcquire()).thenReturn(true);

        validSession = new Session("valid_token_123", "admin", "127.0.0.1", rateLimiter);
        when(sessionManager.getValid("valid_token_123", "127.0.0.1")).thenReturn(validSession);
    }

    @Test
    void testPingAction() throws Exception {
        JsonObject params = new JsonObject();
        CompletableFuture<JsonObject> future = router.route(conn, Protocol.ACTION_PING, params);

        assertNotNull(future);
        JsonObject res = future.get();
        assertTrue(res.get("pong").getAsBoolean());
        assertEquals(Protocol.VERSION, res.get("protocol").getAsInt());
        assertEquals("1.0.0", res.get("pluginVersion").getAsString());
    }

    @Test
    void testUnknownActionReturnsErrUnknownAction() {
        when(conn.session()).thenReturn(validSession);

        JsonObject params = new JsonObject();
        String unknownAction = "nonexistent.custom.action";

        CreeperError error = assertThrows(CreeperError.class, () -> router.route(conn, unknownAction, params));
        assertEquals(Protocol.ERR_UNKNOWN_ACTION, error.code());
        assertTrue(error.getMessage().contains("Unknown action"));
    }

    @Test
    void testMissingParametersReturnsErrInvalidParamsInAuthLogin() throws CreeperError {
        JsonObject params = new JsonObject(); 
        when(authManager.login(eq(params), any(ClientConnection.class)))
                .thenThrow(new CreeperError(Protocol.ERR_INVALID_PARAMS, "username and password are required"));

        CreeperError error = assertThrows(CreeperError.class, () -> router.route(conn, Protocol.ACTION_AUTH_LOGIN, params));
        assertEquals(Protocol.ERR_INVALID_PARAMS, error.code());
    }

    @Test
    void testMissingParametersReturnsErrInvalidParamsInFsCat() {
        when(conn.session()).thenReturn(validSession);

        JsonObject params = new JsonObject(); 

        CreeperError error = assertThrows(CreeperError.class, () -> router.route(conn, Protocol.ACTION_FS_CAT, params));
        assertEquals(Protocol.ERR_INVALID_PARAMS, error.code());
        assertTrue(error.getMessage().contains("path is required"));
    }

    @Test
    void testMissingParametersReturnsErrInvalidParamsInFsTouch() {
        when(conn.session()).thenReturn(validSession);

        JsonObject params = new JsonObject(); 

        CreeperError error = assertThrows(CreeperError.class, () -> router.route(conn, Protocol.ACTION_FS_TOUCH, params));
        assertEquals(Protocol.ERR_INVALID_PARAMS, error.code());
        assertTrue(error.getMessage().contains("path is required"));
    }

    @Test
    void testMissingParametersReturnsErrInvalidParamsInFsMkdir() {
        when(conn.session()).thenReturn(validSession);

        JsonObject params = new JsonObject(); 

        CreeperError error = assertThrows(CreeperError.class, () -> router.route(conn, Protocol.ACTION_FS_MKDIR, params));
        assertEquals(Protocol.ERR_INVALID_PARAMS, error.code());
        assertTrue(error.getMessage().contains("path is required"));
    }

    @Test
    void testUnauthenticatedAccessReturnsErrUnauthorized() {
        when(conn.session()).thenReturn(null); 

        JsonObject params = new JsonObject();

        CreeperError error = assertThrows(CreeperError.class, () -> router.route(conn, Protocol.ACTION_FS_PWD, params));
        assertEquals(Protocol.ERR_UNAUTHORIZED, error.code());
    }
}
