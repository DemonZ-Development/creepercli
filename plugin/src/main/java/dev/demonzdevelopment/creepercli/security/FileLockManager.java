

package dev.demonzdevelopment.creepercli.security;

import dev.demonzdevelopment.creepercli.CreeperError;
import dev.demonzdevelopment.creepercli.PluginConfig;
import dev.demonzdevelopment.creepercli.Protocol;
import dev.demonzdevelopment.creepercli.net.Session;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FileLockManager {
    public record Lock(String token, String user, long expiresAt) {
    }

    private final long ttlMillis;
    private final Map<String, Lock> locks = new ConcurrentHashMap<>();

    public FileLockManager(PluginConfig cfg) {
        this.ttlMillis = cfg.fileLockTtlMillis();
    }

    public long ttlMillis() {
        return ttlMillis;
    }

    public Lock acquire(Session session, Path path) throws CreeperError {
        String key = path.toAbsolutePath().normalize().toString();
        long now = System.currentTimeMillis();
        Lock current = locks.get(key);
        if (current != null && current.expiresAt() > now && !current.token().equals(session.token)) {
            throw new CreeperError(Protocol.ERR_LOCKED,
                    "File is locked by " + current.user() + " for " + ((current.expiresAt() - now) / 1000) + "s");
        }
        Lock lock = new Lock(session.token, session.username, now + ttlMillis);
        locks.put(key, lock);
        return lock;
    }

    public void renew(Session session, Path path) throws CreeperError {
        String key = path.toAbsolutePath().normalize().toString();
        long now = System.currentTimeMillis();
        Lock current = locks.get(key);
        if (current == null || current.expiresAt() <= now) {
            throw new CreeperError(Protocol.ERR_LOCKED, "Lock expired, acquire it again (edit)");
        }
        if (!current.token().equals(session.token)) {
            throw new CreeperError(Protocol.ERR_LOCKED, "Lock is held by " + current.user());
        }
        locks.put(key, new Lock(session.token, session.username, now + ttlMillis));
    }

    public void release(Session session, Path path) {
        String key = path.toAbsolutePath().normalize().toString();
        Lock current = locks.get(key);
        if (current != null && current.token().equals(session.token)) {
            locks.remove(key);
        }
    }

    public void releaseByToken(String token) {
        locks.entrySet().removeIf(e -> e.getValue().token().equals(token));
    }

    public void sweep() {
        long now = System.currentTimeMillis();
        locks.entrySet().removeIf(e -> e.getValue().expiresAt() <= now);
    }

    public void sweepAll() {
        locks.clear();
    }

    public int count() {
        return locks.size();
    }
}
