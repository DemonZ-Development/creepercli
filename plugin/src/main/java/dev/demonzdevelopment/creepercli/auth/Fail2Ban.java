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
        bannedUntil.remove(ip);
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
        deque.addLast(now);
        deque.removeIf(t -> now - t > windowMillis);
        if (deque.size() >= maxFailures) {
            bannedUntil.put(ip, now + banMillis);
            deque.clear();
        }
    }

    public void clear(String ip) {
        failures.remove(ip);
        bannedUntil.remove(ip);
    }

    public void sweep() {
        long now = System.currentTimeMillis();
        bannedUntil.entrySet().removeIf(e -> e.getValue() <= now);
        failures.entrySet().removeIf(e -> e.getValue().peekFirst() != null && now - e.getValue().peekFirst() > windowMillis);
    }

    public int bannedCount() {
        sweep();
        return bannedUntil.size();
    }
}
