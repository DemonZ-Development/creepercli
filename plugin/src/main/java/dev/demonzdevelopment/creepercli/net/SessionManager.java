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

package dev.demonzdevelopment.creepercli.net;

import dev.demonzdevelopment.creepercli.PluginConfig;
import dev.demonzdevelopment.creepercli.security.RateLimiter;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager {
    private final long timeoutMillis;
    private final double commandsPerSecond;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public SessionManager(PluginConfig cfg) {
        this.timeoutMillis = cfg.sessionTimeoutMillis();
        this.commandsPerSecond = cfg.commandsPerSecond();
    }

    public Session create(String username, String ip) {
        Session s = new Session(
                UUID.randomUUID().toString().replace("-", ""),
                username,
                ip,
                new RateLimiter(commandsPerSecond, commandsPerSecond));
        sessions.put(s.token, s);
        return s;
    }

    public Session getValid(String token, String ip) {
        if (token == null) return null;
        Session s = sessions.get(token);
        if (s == null) return null;
        if (!s.ip.equals(ip)) {
            sessions.remove(token);
            return null;
        }
        long now = System.currentTimeMillis();
        if (now - s.lastSeen > timeoutMillis) {
            sessions.remove(token);
            return null;
        }
        s.lastSeen = now;
        return s;
    }

    public long remainingMillis(String token) {
        Session s = sessions.get(token);
        if (s == null) return 0;
        return Math.max(0, timeoutMillis - (System.currentTimeMillis() - s.lastSeen));
    }

    public void invalidate(String token) {
        sessions.remove(token);
    }

    public void invalidateUserExcept(String username, String exceptToken) {
        sessions.entrySet().removeIf(e -> e.getValue().username.equals(username) && !e.getValue().token.equals(exceptToken));
    }

    public void sweep() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(e -> now - e.getValue().lastSeen > timeoutMillis);
    }

    public int count() {
        return sessions.size();
    }

    public Collection<Session> all() {
        return sessions.values();
    }
}
