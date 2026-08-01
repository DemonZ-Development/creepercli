package dev.demonzdevelopment.creepercli.security;

public final class RateLimiter {
    private final double capacity;
    private final double ratePerSecond;
    private double tokens;
    private long lastRefillNanos;

    public RateLimiter(double capacity, double ratePerSecond) {
        this.capacity = capacity;
        this.ratePerSecond = ratePerSecond;
        this.tokens = capacity;
        this.lastRefillNanos = System.nanoTime();
    }

    public synchronized boolean tryAcquire() {
        long now = System.nanoTime();
        double elapsed = (now - lastRefillNanos) / 1_000_000_000.0;
        tokens = Math.min(capacity, tokens + elapsed * ratePerSecond);
        lastRefillNanos = now;
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }
}
