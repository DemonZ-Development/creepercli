package dev.demonzdevelopment.creepercli.auth;

import dev.demonzdevelopment.creepercli.PluginConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthLimiterTest {
    @Test
    void resetRemovesSuccessfulLoginState() {
        PluginConfig cfg = mock(PluginConfig.class);
        when(cfg.loginMaxAttempts()).thenReturn(3);
        when(cfg.loginWindowMillis()).thenReturn(300_000);
        AuthLimiter limiter = new AuthLimiter(cfg);

        assertTrue(limiter.tryAcquire("127.0.0.1"));
        assertEquals(1, limiter.trackedIpCount());
        limiter.reset("127.0.0.1");
        assertEquals(0, limiter.trackedIpCount());
    }

    @Test
    void sweepRemovesExpiredIpEntries() throws Exception {
        PluginConfig cfg = mock(PluginConfig.class);
        when(cfg.loginMaxAttempts()).thenReturn(3);
        when(cfg.loginWindowMillis()).thenReturn(1);
        AuthLimiter limiter = new AuthLimiter(cfg);

        assertTrue(limiter.tryAcquire("198.51.100.1"));
        Thread.sleep(5);
        limiter.sweep();
        assertEquals(0, limiter.trackedIpCount());
    }
}
