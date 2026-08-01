package dev.demonzdevelopment.creepercli.monitor;

import com.google.gson.JsonObject;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.FileStore;
import java.nio.file.Files;

public final class StatsCollector {
    private final CreeperCLIPlugin plugin;

    public StatsCollector(CreeperCLIPlugin plugin) {
        this.plugin = plugin;
    }

    public JsonObject collect() {
        JsonObject stats = new JsonObject();
        stats.addProperty("uptimeMs", plugin.uptimeMillis());

        Runtime rt = Runtime.getRuntime();
        JsonObject memory = new JsonObject();
        memory.addProperty("heapUsed", rt.totalMemory() - rt.freeMemory());
        memory.addProperty("heapTotal", rt.totalMemory());
        memory.addProperty("heapMax", rt.maxMemory());
        stats.add("memory", memory);

        JsonObject cpu = new JsonObject();
        double load = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
        cpu.addProperty("load", load < 0 ? -1.0 : load);
        cpu.addProperty("processCpuPercent", -1.0);
        cpu.addProperty("systemCpuPercent", -1.0);
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean ext) {
            try {
                double p = ext.getProcessCpuLoad();
                double s = ext.getSystemCpuLoad();
                cpu.addProperty("processCpuPercent", p < 0 ? -1.0 : Math.max(0, p) * 100.0);
                cpu.addProperty("systemCpuPercent", s < 0 ? -1.0 : Math.max(0, s) * 100.0);
            } catch (Throwable ignored) {
            }
        }
        stats.add("cpu", cpu);

        JsonObject disk = new JsonObject();
        try {
            FileStore store = Files.getFileStore(plugin.serverRoot());
            long total = store.getTotalSpace();
            long usable = store.getUsableSpace();
            disk.addProperty("total", total);
            disk.addProperty("usable", usable);
            disk.addProperty("used", total - usable);
        } catch (IOException e) {
            disk.addProperty("total", 0L);
            disk.addProperty("usable", 0L);
            disk.addProperty("used", 0L);
        }
        stats.add("disk", disk);

        JsonObject jvm = new JsonObject();
        jvm.addProperty("javaVersion", System.getProperty("java.version"));
        jvm.addProperty("availableProcessors", rt.availableProcessors());
        stats.add("jvm", jvm);
        return stats;
    }
}
