

package dev.demonzdevelopment.creepercli.security;

import dev.demonzdevelopment.creepercli.CreeperError;
import dev.demonzdevelopment.creepercli.PluginConfig;
import dev.demonzdevelopment.creepercli.Protocol;
import dev.demonzdevelopment.creepercli.net.Session;
import dev.demonzdevelopment.creepercli.security.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileLockManagerTest {

    private FileLockManager locks;

    @BeforeEach
    void setUp() {
        PluginConfig cfg = mock(PluginConfig.class);
        when(cfg.fileLockTtlMillis()).thenReturn(5_000);
        locks = new FileLockManager(cfg);
    }

    private Session session(String token, String user) {
        
        return new Session(token, user, "127.0.0.1", new RateLimiter(10, 10));
    }

    @Test
    void acquireReturnsLockForSameSession() throws CreeperError {
        Path p = Path.of("/tmp/server.properties");
        Session s = session("tok1", "admin");
        FileLockManager.Lock lock = locks.acquire(s, p);
        assertNotNull(lock);
        assertEquals("tok1", lock.token());
        assertEquals("admin", lock.user());
        assertTrue(lock.expiresAt() > System.currentTimeMillis());
    }

    @Test
    void acquireRejectedWhenLockedByAnotherSession() throws CreeperError {
        Path p = Path.of("/tmp/server.properties");
        locks.acquire(session("tok1", "alice"), p);
        CreeperError err = assertThrows(CreeperError.class, () -> locks.acquire(session("tok2", "bob"), p));
        assertEquals(Protocol.ERR_LOCKED, err.code());
        assertTrue(err.getMessage().contains("alice"));
    }

    @Test
    void renewFailsWhenLockExpired() throws CreeperError {
        Path p = Path.of("/tmp/server.properties");
        Session s = session("tok1", "admin");
        FileLockManager.Lock lock = locks.acquire(s, p);
        assertNotNull(lock);
        assertTrue(lock.expiresAt() > 0);
        
        CreeperError err = assertThrows(CreeperError.class, () -> locks.renew(s, Path.of("/tmp/never-locked.txt")));
        assertEquals(Protocol.ERR_LOCKED, err.code());
    }

    @Test
    void renewFailsWhenOwnedByDifferentSession() throws CreeperError {
        Path p = Path.of("/tmp/server.properties");
        locks.acquire(session("tok1", "alice"), p);
        CreeperError err = assertThrows(CreeperError.class, () -> locks.renew(session("tok2", "bob"), p));
        assertEquals(Protocol.ERR_LOCKED, err.code());
        assertTrue(err.getMessage().contains("alice"));
    }

    @Test
    void renewSucceedsForOwner() throws CreeperError {
        Path p = Path.of("/tmp/server.properties");
        Session s = session("tok1", "admin");
        FileLockManager.Lock initial = locks.acquire(s, p);
        long ttl = locks.ttlMillis();
        
        try { Thread.sleep(2); } catch (InterruptedException ignored) {}
        locks.renew(s, p);
        assertEquals(1, locks.count());
        assertEquals(ttl, locks.ttlMillis());
        assertNotNull(initial);
    }

    @Test
    void releaseRemovesLock() throws CreeperError {
        Path p = Path.of("/tmp/server.properties");
        Session s = session("tok1", "admin");
        locks.acquire(s, p);
        locks.release(s, p);
        assertEquals(0, locks.count());
    }

    @Test
    void releaseByTokenRemovesLocksForThatTokenOnly() throws CreeperError {
        Path a = Path.of("/tmp/a.txt");
        Path b = Path.of("/tmp/b.txt");
        Session s1 = session("tok1", "alice");
        Session s2 = session("tok2", "bob");
        locks.acquire(s1, a);
        locks.acquire(s2, b);
        locks.releaseByToken("tok1");
        assertEquals(1, locks.count());
        
        locks.renew(s2, b);
    }

    @Test
    void sweepRemovesExpiredLocks() throws CreeperError, InterruptedException {
        PluginConfig cfg = mock(PluginConfig.class);
        when(cfg.fileLockTtlMillis()).thenReturn(50);
        FileLockManager shortLived = new FileLockManager(cfg);
        shortLived.acquire(session("tok1", "admin"), Path.of("/tmp/old.txt"));
        Thread.sleep(80);
        shortLived.sweep();
        assertEquals(0, shortLived.count());
    }

    @Test
    void sweepAllClearsEverything() throws CreeperError {
        locks.acquire(session("tok1", "a"), Path.of("/tmp/x"));
        locks.acquire(session("tok2", "b"), Path.of("/tmp/y"));
        locks.sweepAll();
        assertEquals(0, locks.count());
    }
}