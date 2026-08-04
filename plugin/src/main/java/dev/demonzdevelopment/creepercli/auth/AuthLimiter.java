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
