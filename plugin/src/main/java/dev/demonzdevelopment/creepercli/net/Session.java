

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
