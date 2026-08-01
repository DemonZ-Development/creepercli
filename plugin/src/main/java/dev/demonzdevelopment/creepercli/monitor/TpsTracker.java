package dev.demonzdevelopment.creepercli.monitor;

import com.google.gson.JsonObject;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;

import java.util.concurrent.atomic.AtomicInteger;

public final class TpsTracker {
    private static final int SAMPLES = 20 * 60 * 15;

    private final CreeperCLIPlugin plugin;
    private final long[] samples = new long[SAMPLES];
    private final AtomicInteger cursor = new AtomicInteger();
    private volatile long lastTick = -1;
    private int taskId = -1;

    public TpsTracker(CreeperCLIPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        lastTick = -1;
        taskId = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::onTick, 1L, 1L).getTaskId();
    }

    public void stop() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    private void onTick() {
        long now = System.nanoTime();
        long last = lastTick;
        lastTick = now;
        if (last == -1) return;
        samples[cursor.getAndIncrement() % SAMPLES] = now - last;
    }

    public double tps(int windowSeconds) {
        int n = Math.min(SAMPLES, windowSeconds * 20);
        long sum = 0;
        int count = 0;
        int idx = cursor.get();
        for (int i = 0; i < n; i++) {
            long v = samples[(idx - 1 - i + SAMPLES) % SAMPLES];
            if (v > 0) {
                sum += v;
                count++;
            }
        }
        if (count == 0) return 20.0;
        return Math.min(20.0, count * 50_000_000.0 / sum);
    }

    public JsonObject snapshot() {
        JsonObject o = new JsonObject();
        o.addProperty("tps1m", round(tps(60)));
        o.addProperty("tps5m", round(tps(300)));
        o.addProperty("tps15m", round(tps(900)));
        o.addProperty("tickMs", round(50.0 / tps(60)));
        return o;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
