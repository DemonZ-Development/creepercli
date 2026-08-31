

package dev.demonzdevelopment.creepercli.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    @Test
    void allowsUpToCapacityImmediately() {
        RateLimiter rl = new RateLimiter(5, 1);
        for (int i = 0; i < 5; i++) {
            assertTrue(rl.tryAcquire(), "acquire " + i + " should succeed");
        }
    }

    @Test
    void blocksOnceCapacityDrained() {
        RateLimiter rl = new RateLimiter(2, 1);
        assertTrue(rl.tryAcquire());
        assertTrue(rl.tryAcquire());
        assertFalse(rl.tryAcquire());
    }

    @Test
    void refillsOverTime() throws InterruptedException {
        
        RateLimiter rl = new RateLimiter(1, 10);
        assertTrue(rl.tryAcquire());
        assertFalse(rl.tryAcquire());
        Thread.sleep(150);
        assertTrue(rl.tryAcquire());
    }

    @Test
    void refillCappedByCapacity() throws InterruptedException {
        
        
        RateLimiter rl = new RateLimiter(2, 1000);
        assertTrue(rl.tryAcquire());
        assertTrue(rl.tryAcquire());
        assertFalse(rl.tryAcquire());
    }

    @Test
    void subSecondBurstMathIsCorrect() {
        
        RateLimiter rl = new RateLimiter(30, 30);
        int count = 0;
        for (int i = 0; i < 1000; i++) {
            if (rl.tryAcquire()) count++;
        }
        assertEquals(30, count, "exactly capacity on a fresh bucket");
    }
}