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

import dev.demonzdevelopment.creepercli.security.RateLimiter;

public final class Session {
    public final String token;
    public final String username;
    public final String ip;
    public final long createdAt;
    public final RateLimiter limiter;
    public volatile long lastSeen;
    public volatile String cwd = "";

    public Session(String token, String username, String ip, RateLimiter limiter) {
        this.token = token;
        this.username = username;
        this.ip = ip;
        this.limiter = limiter;
        this.createdAt = System.currentTimeMillis();
        this.lastSeen = createdAt;
    }

    public static String cwdString(Session session) {
        String cwd = session.cwd;
        return cwd.isEmpty() ? "/" : "/" + cwd;
    }
}
