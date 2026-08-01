package dev.demonzdevelopment.creepercli.auth;

import dev.demonzdevelopment.creepercli.PluginConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class AuthLimiter {
    private final int maxAttempts;
    private final long windowMillis;
    private final Map<String, ConcurrentLinkedDeque<Long>> attempts = new ConcurrentHashMap<>();

    public AuthLimiter(PluginConfig cfg) {
        this.maxAttempts = cfg.loginMaxAttempts();
        this.windowMillis = cfg.loginWindowMillis();
    }

    public boolean tryAcquire(String ip) {
        long now = System.currentTimeMillis();
        ConcurrentLinkedDeque<Long> deque = attempts.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>());
        deque.removeIf(t -> now - t > windowMillis);
        if (deque.size() >= maxAttempts) {
            return false;
        }
        deque.addLast(now);
        return true;
    }

    public void reset(String ip) {
        attempts.remove(ip);
    }
}
