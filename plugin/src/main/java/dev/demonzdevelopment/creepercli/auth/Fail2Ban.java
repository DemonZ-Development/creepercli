

package dev.demonzdevelopment.creepercli.auth;

import dev.demonzdevelopment.creepercli.PluginConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class Fail2Ban {
    private final int maxFailures;
    private final long windowMillis;
    private final long banMillis;
    private final Map<String, ConcurrentLinkedDeque<Long>> failures = new ConcurrentHashMap<>();
    private final Map<String, Long> bannedUntil = new ConcurrentHashMap<>();

    public Fail2Ban(PluginConfig cfg) {
        this.maxFailures = cfg.fail2banMaxFailures();
        this.windowMillis = cfg.fail2banWindowMillis();
        this.banMillis = cfg.fail2banBanMillis();
    }

    public boolean isBanned(String ip) {
        Long until = bannedUntil.get(ip);
        if (until == null) return false;
        if (until > System.currentTimeMillis()) return true;
        bannedUntil.remove(ip, until);
        return false;
    }

    public long banRemainingSeconds(String ip) {
        Long until = bannedUntil.get(ip);
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis()) / 1000);
    }

    public void recordFailure(String ip) {
        long now = System.currentTimeMillis();
        ConcurrentLinkedDeque<Long> deque = failures.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>());
        synchronized (deque) {
            deque.addLast(now);
            while (deque.peekFirst() != null && now - deque.peekFirst() > windowMillis) {
                deque.removeFirst();
            }
            if (deque.size() >= maxFailures) {
                bannedUntil.put(ip, now + banMillis);
                deque.clear();
            }
        }
    }

    public void clear(String ip) {
        failures.remove(ip);
        bannedUntil.remove(ip);
    }

    public void sweep() {
        long now = System.currentTimeMillis();
        bannedUntil.entrySet().removeIf(e -> e.getValue() <= now);
        failures.entrySet().removeIf(entry -> {
            ConcurrentLinkedDeque<Long> deque = entry.getValue();
            synchronized (deque) {
                while (deque.peekFirst() != null && now - deque.peekFirst() > windowMillis) {
                    deque.removeFirst();
                }
                return deque.isEmpty();
            }
        });
    }

    public int bannedCount() {
        sweep();
        return bannedUntil.size();
    }
}
