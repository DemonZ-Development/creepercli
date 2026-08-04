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

package dev.demonzdevelopment.creepercli.security;

import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.CreeperError;
import dev.demonzdevelopment.creepercli.Protocol;
import dev.demonzdevelopment.creepercli.net.Session;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AuditLogger {
    private final CreeperCLIPlugin plugin;
    private final Path file;
    private final long maxBytes;
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(10_000);
    private final AtomicBoolean running = new AtomicBoolean();
    private Thread thread;

    public AuditLogger(CreeperCLIPlugin plugin) {
        this.plugin = plugin;
        this.file = new java.io.File(plugin.getDataFolder(), "creepercli-audit.log").toPath();
        this.maxBytes = plugin.cfg().auditMaxMb() * 1024L * 1024;
    }

    public void start() {
        running.set(true);
        thread = new Thread(this::loop, "creepercli-audit");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running.set(false);
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(2000);
            } catch (InterruptedException ignored) {
            }
        }
    }

    public void log(String ip, String user, String action, String params) {
        String line = String.format("%s | %s | %s | %s | %s",
                Instant.now(),
                ip == null ? "-" : ip,
                user == null ? "-" : user,
                action,
                truncate(params, 500));
        queue.offer(line);
    }

    private void loop() {
        while (running.get() || !queue.isEmpty()) {
            String line;
            try {
                line = queue.poll(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                break;
            }
            if (line == null) continue;
            try {
                write(line);
            } catch (Exception e) {
                plugin.getLogger().warning("Audit write failed: " + e.getMessage());
            }
        }
    }

    private synchronized void write(String line) throws java.io.IOException {
        try {
            if (java.nio.file.Files.exists(file) && java.nio.file.Files.size(file) > maxBytes) {
                Path rotated = file.resolveSibling(file.getFileName().toString() + ".1");
                java.nio.file.Files.move(file, rotated, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, line + System.lineSeparator(),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (java.nio.file.NoSuchFileException ignored) {
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
