

package dev.demonzdevelopment.creepercli.net;

import dev.demonzdevelopment.creepercli.PluginConfig;
import dev.demonzdevelopment.creepercli.security.RateLimiter;

import java.util.Collection;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final long timeoutMillis;
    private final double commandsPerSecond;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public SessionManager(PluginConfig cfg) {
        this.timeoutMillis = cfg.sessionTimeoutMillis();
        this.commandsPerSecond = cfg.commandsPerSecond();
    }

    public Session create(String username, String ip) {
        Session s = new Session(
                newToken(),
                username,
                ip,
                new RateLimiter(commandsPerSecond, commandsPerSecond));
        sessions.put(s.token, s);
        return s;
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
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

    public int invalidateUserExcept(String username, String exceptToken) {
        int removed = 0;
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            Session session = entry.getValue();
            if (session.username.equals(username)
                    && (exceptToken == null || !session.token.equals(exceptToken))
                    && sessions.remove(entry.getKey(), session)) {
                removed++;
            }
        }
        return removed;
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
