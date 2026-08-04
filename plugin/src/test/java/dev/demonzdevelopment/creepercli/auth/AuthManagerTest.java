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

package dev.demonzdevelopment.creepercli.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.google.gson.JsonObject;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.CreeperError;
import dev.demonzdevelopment.creepercli.Hashes;
import dev.demonzdevelopment.creepercli.PluginConfig;
import dev.demonzdevelopment.creepercli.Protocol;
import dev.demonzdevelopment.creepercli.net.ClientConnection;
import dev.demonzdevelopment.creepercli.net.Session;
import dev.demonzdevelopment.creepercli.net.SessionManager;
import dev.demonzdevelopment.creepercli.security.AuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthManagerTest {

    @TempDir
    Path tempDir;

    private CreeperCLIPlugin plugin;
    private PluginConfig cfg;
    private UserStore userStore;
    private TotpManager totpManager;
    private Fail2Ban fail2ban;
    private AuthLimiter authLimiter;
    private SessionManager sessionManager;
    private AuditLogger auditLogger;
    private AuthManager authManager;

    @BeforeEach
    void setUp() {
        plugin = mock(CreeperCLIPlugin.class);
        cfg = mock(PluginConfig.class);
        userStore = mock(UserStore.class);
        totpManager = new TotpManager();
        fail2ban = mock(Fail2Ban.class);
        authLimiter = mock(AuthLimiter.class);
        auditLogger = mock(AuditLogger.class);

        when(cfg.sessionTimeoutMillis()).thenReturn(15 * 60_000);
        when(cfg.commandsPerSecond()).thenReturn(30.0);
        sessionManager = new SessionManager(cfg);

        when(plugin.cfg()).thenReturn(cfg);
        when(plugin.users()).thenReturn(userStore);
        when(plugin.totp()).thenReturn(totpManager);
        when(plugin.fail2ban()).thenReturn(fail2ban);
        when(plugin.authLimiter()).thenReturn(authLimiter);
        when(plugin.sessions()).thenReturn(sessionManager);
        when(plugin.audit()).thenReturn(auditLogger);

        authManager = new AuthManager(plugin);
    }


    @Test
    void testSha256Hex() {
        String input = "hello";
        String expectedHash = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";
        assertEquals(expectedHash, Hashes.sha256Hex(input));
        assertEquals(expectedHash, Hashes.sha256Hex(input.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void testSha256File() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello");
        String expectedHash = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";
        assertEquals(expectedHash, Hashes.sha256File(file));
    }

    @Test
    void testBcryptPasswordHashingAndVerification() {
        String rawPassword = "SecretPassword123!";
        String hash = BCrypt.withDefaults().hashToString(12, rawPassword.toCharArray());

        assertNotNull(hash);
        assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2b$"));

        assertTrue(BCrypt.verifyer().verify(rawPassword.toCharArray(), hash).verified);

        assertFalse(BCrypt.verifyer().verify("WrongPassword".toCharArray(), hash).verified);
    }

    @Test
    void testChangePasswordSuccess() throws CreeperError {
        String username = "admin";
        String oldPassword = "oldSecretPassword123";
        String oldHash = BCrypt.withDefaults().hashToString(12, oldPassword.toCharArray());
        User user = new User(username, oldHash, null);

        Session session = sessionManager.create(username, "127.0.0.1");
        ClientConnection conn = mock(ClientConnection.class);
        when(conn.session()).thenReturn(session);
        when(userStore.get(username)).thenReturn(user);

        JsonObject params = new JsonObject();
        params.addProperty("oldPassword", oldPassword);
        params.addProperty("newPassword", "newSecretPassword456");

        JsonObject res = authManager.changePassword(conn, params);
        assertNotNull(res);

        verify(userStore).setPassword(eq(username), anyString());
        verify(auditLogger).log("127.0.0.1", username, "auth.passwd", "changed");
    }

    @Test
    void testChangePasswordInvalidOldPassword() {
        String username = "admin";
        String oldPassword = "oldSecretPassword123";
        String oldHash = BCrypt.withDefaults().hashToString(12, oldPassword.toCharArray());
        User user = new User(username, oldHash, null);

        Session session = sessionManager.create(username, "127.0.0.1");
        ClientConnection conn = mock(ClientConnection.class);
        when(conn.session()).thenReturn(session);
        when(userStore.get(username)).thenReturn(user);

        JsonObject params = new JsonObject();
        params.addProperty("oldPassword", "wrongPassword");
        params.addProperty("newPassword", "newSecretPassword456");

        CreeperError err = assertThrows(CreeperError.class, () -> authManager.changePassword(conn, params));
        assertEquals(Protocol.ERR_AUTH_FAILED, err.code());
    }

    @Test
    void testChangePasswordTooShortNewPassword() {
        String username = "admin";
        String oldPassword = "oldSecretPassword123";
        String oldHash = BCrypt.withDefaults().hashToString(12, oldPassword.toCharArray());
        User user = new User(username, oldHash, null);

        Session session = sessionManager.create(username, "127.0.0.1");
        ClientConnection conn = mock(ClientConnection.class);
        when(conn.session()).thenReturn(session);
        when(userStore.get(username)).thenReturn(user);

        JsonObject params = new JsonObject();
        params.addProperty("oldPassword", oldPassword);
        params.addProperty("newPassword", "short");

        CreeperError err = assertThrows(CreeperError.class, () -> authManager.changePassword(conn, params));
        assertEquals(Protocol.ERR_INVALID_PARAMS, err.code());
    }


    @Test
    void testTotpSecretGeneration() {
        String secret = totpManager.generateSecret();
        assertNotNull(secret);
        assertEquals(32, secret.length());
        assertTrue(secret.matches("[A-Z2-7]+"));
    }

    @Test
    void testTotpCodeGenerationAndVerification() {
        String secret = totpManager.generateSecret();
        long nowSeconds = System.currentTimeMillis() / 1000;
        String code = totpManager.generateCode(secret, nowSeconds);

        assertNotNull(code);
        assertEquals(6, code.length());
        assertTrue(code.matches("\\d{6}"));

        assertTrue(totpManager.verify(secret, code));

        assertFalse(totpManager.verify(secret, "000000".equals(code) ? "111111" : "000000"));
        assertFalse(totpManager.verify(secret, "invalid"));
        assertFalse(totpManager.verify(secret, null));
        assertFalse(totpManager.verify(null, code));
    }

    @Test
    void testTotpOtpauthUri() {
        String secret = "JBSWY3DPEHPK3PXP";
        String uri = totpManager.otpauthUri("testuser", secret);
        assertNotNull(uri);
        assertTrue(uri.startsWith("otpauth://totp/CreeperCLI%3Atestuser?secret=" + secret));
    }

    @Test
    void testTotpSetupAndVerifyFlow() throws CreeperError {
        String username = "testuser";
        String password = "validPassword123";
        String hash = BCrypt.withDefaults().hashToString(12, password.toCharArray());
        User user = new User(username, hash, null);

        Session session = sessionManager.create(username, "127.0.0.1");
        ClientConnection conn = mock(ClientConnection.class);
        when(conn.session()).thenReturn(session);
        when(userStore.get(username)).thenReturn(user);

        JsonObject setupParams = new JsonObject();
        setupParams.addProperty("password", password);
        JsonObject setupRes = authManager.totpSetup(conn, setupParams);

        assertNotNull(setupRes);
        assertTrue(setupRes.has("secret"));
        assertTrue(setupRes.has("otpauthUrl"));
        String secret = setupRes.get("secret").getAsString();

        String validCode = totpManager.generateCode(secret, System.currentTimeMillis() / 1000);
        JsonObject verifyParams = new JsonObject();
        verifyParams.addProperty("code", validCode);
        JsonObject verifyRes = authManager.totpVerify(conn, verifyParams);

        assertNotNull(verifyRes);
        assertTrue(verifyRes.get("totpEnabled").getAsBoolean());
        verify(userStore).setTotp(username, secret);
    }


    @Test
    void testSessionTokenGenerationAndValidation() {
        String username = "admin";
        String ip = "192.168.1.50";

        Session s = sessionManager.create(username, ip);
        assertNotNull(s);
        assertNotNull(s.token);
        assertEquals(32, s.token.length());
        assertEquals(username, s.username);
        assertEquals(ip, s.ip);

        Session valid = sessionManager.getValid(s.token, ip);
        assertNotNull(valid);
        assertEquals(s.token, valid.token);

        assertNull(sessionManager.getValid(s.token, "192.168.1.99"));

        assertNull(sessionManager.getValid(null, ip));
        assertNull(sessionManager.getValid("nonexistent_token", ip));
    }

    @Test
    void testSessionExpiration() {
        String username = "admin";
        String ip = "127.0.0.1";

        Session s = sessionManager.create(username, ip);
        assertNotNull(s);

        s.lastSeen = System.currentTimeMillis() - 1_000_000L;

        assertNull(sessionManager.getValid(s.token, ip));
        assertEquals(0, sessionManager.count());
    }

    @Test
    void testSessionRemainingMillis() {
        String username = "user";
        String ip = "127.0.0.1";

        Session s = sessionManager.create(username, ip);
        long remaining = sessionManager.remainingMillis(s.token);
        assertTrue(remaining > 0 && remaining <= 15 * 60_000);

        assertEquals(0, sessionManager.remainingMillis("invalid_token"));
    }

    @Test
    void testSessionInvalidation() {
        String username = "user";
        String ip = "127.0.0.1";

        Session s1 = sessionManager.create(username, ip);
        Session s2 = sessionManager.create(username, ip);
        assertEquals(2, sessionManager.count());

        sessionManager.invalidate(s1.token);
        assertNull(sessionManager.getValid(s1.token, ip));
        assertNotNull(sessionManager.getValid(s2.token, ip));

        sessionManager.invalidateUserExcept(username, s2.token);
        assertNotNull(sessionManager.getValid(s2.token, ip));
    }

    @Test
    void testSessionSweep() {
        String ip = "127.0.0.1";
        Session active = sessionManager.create("activeUser", ip);
        Session expired = sessionManager.create("expiredUser", ip);

        expired.lastSeen = System.currentTimeMillis() - 2_000_000L;
        sessionManager.sweep();

        assertEquals(1, sessionManager.count());
        assertNotNull(sessionManager.getValid(active.token, ip));
    }
}
