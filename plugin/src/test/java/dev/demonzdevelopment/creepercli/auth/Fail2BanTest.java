

package dev.demonzdevelopment.creepercli.auth;

import dev.demonzdevelopment.creepercli.PluginConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Fail2BanTest {

    private PluginConfig cfg;

    @BeforeEach
    void setUp() {
        cfg = mock(PluginConfig.class);
        when(cfg.fail2banMaxFailures()).thenReturn(3);
        when(cfg.fail2banWindowMillis()).thenReturn(10_000);
        when(cfg.fail2banBanMillis()).thenReturn(600_000);
    }

    @Test
    void testInitialStateNotBanned() {
        Fail2Ban fail2ban = new Fail2Ban(cfg);
        assertFalse(fail2ban.isBanned("192.168.1.1"));
        assertEquals(0, fail2ban.banRemainingSeconds("192.168.1.1"));
        assertEquals(0, fail2ban.bannedCount());
    }

    @Test
    void testTrackingFailedAttemptsPerIp() {
        Fail2Ban fail2ban = new Fail2Ban(cfg);

        String ip1 = "192.168.1.1";
        String ip2 = "192.168.1.2";

        fail2ban.recordFailure(ip1);
        assertFalse(fail2ban.isBanned(ip1));

        fail2ban.recordFailure(ip1);
        assertFalse(fail2ban.isBanned(ip1));

        fail2ban.recordFailure(ip2);
        assertFalse(fail2ban.isBanned(ip2));

        assertEquals(0, fail2ban.bannedCount());
    }

    @Test
    void testIpBanningThreshold() {
        Fail2Ban fail2ban = new Fail2Ban(cfg);

        String ip1 = "192.168.1.1";
        String ip2 = "192.168.1.2";

        fail2ban.recordFailure(ip1);
        fail2ban.recordFailure(ip1);
        fail2ban.recordFailure(ip1);

        assertTrue(fail2ban.isBanned(ip1));
        assertTrue(fail2ban.banRemainingSeconds(ip1) > 0);
        assertEquals(1, fail2ban.bannedCount());

        assertFalse(fail2ban.isBanned(ip2));
    }

    @Test
    void testBanExpiration() throws InterruptedException {
        when(cfg.fail2banBanMillis()).thenReturn(100); 
        Fail2Ban fail2ban = new Fail2Ban(cfg);

        String ip = "10.0.0.1";
        fail2ban.recordFailure(ip);
        fail2ban.recordFailure(ip);
        fail2ban.recordFailure(ip);

        assertTrue(fail2ban.isBanned(ip));

        Thread.sleep(150);

        assertFalse(fail2ban.isBanned(ip));
        assertEquals(0, fail2ban.banRemainingSeconds(ip));
    }

    @Test
    void testClearResetLogic() {
        Fail2Ban fail2ban = new Fail2Ban(cfg);

        String ip = "10.0.0.2";
        fail2ban.recordFailure(ip);
        fail2ban.recordFailure(ip);
        fail2ban.recordFailure(ip);

        assertTrue(fail2ban.isBanned(ip));

        fail2ban.clear(ip);
        assertFalse(fail2ban.isBanned(ip));

        fail2ban.recordFailure(ip);
        assertFalse(fail2ban.isBanned(ip));
    }

    @Test
    void testWindowExpiration() throws InterruptedException {
        when(cfg.fail2banWindowMillis()).thenReturn(100); 
        Fail2Ban fail2ban = new Fail2Ban(cfg);

        String ip = "10.0.0.3";
        fail2ban.recordFailure(ip);
        fail2ban.recordFailure(ip);

        Thread.sleep(150);

        fail2ban.recordFailure(ip);
        assertFalse(fail2ban.isBanned(ip));
    }

    @Test
    void testSweepRemovesExpiredBans() throws InterruptedException {
        when(cfg.fail2banBanMillis()).thenReturn(100);
        Fail2Ban fail2ban = new Fail2Ban(cfg);

        String ip = "10.0.0.4";
        fail2ban.recordFailure(ip);
        fail2ban.recordFailure(ip);
        fail2ban.recordFailure(ip);

        assertEquals(1, fail2ban.bannedCount());

        Thread.sleep(150);

        fail2ban.sweep();
        assertEquals(0, fail2ban.bannedCount());
    }
}
