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

package dev.demonzdevelopment.creepercli.bukkit;

import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.CreeperError;
import dev.demonzdevelopment.creepercli.Protocol;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class BukkitBridge {
    public record ExecResult(boolean success, List<String> lines, long elapsedMs) {
    }

    private final CreeperCLIPlugin plugin;

    public BukkitBridge(CreeperCLIPlugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<ExecResult> exec(String command, int timeoutSeconds) {
        CompletableFuture<ExecResult> future = new CompletableFuture<>();
        CapturingSender sender = new CapturingSender();
        long mark = plugin.logs().mark();
        java.io.File logFile = new java.io.File(plugin.cfg().serverRoot().toFile(), "logs/latest.log");
        long fileOffset = logFile.isFile() ? logFile.length() : -1;
        long start = System.nanoTime();
        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean ok;
            try {
                ok = Bukkit.getServer().dispatchCommand(sender.asSender(), command);
            } catch (Throwable t) {
                plugin.getLogger().warning("exec threw on main thread: " + t);
                future.completeExceptionally(new CreeperError(Protocol.ERR_INTERNAL, "Command execution failed: " + t.getMessage()));
                return;
            }
            boolean okFinal = ok;
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                long elapsed = (System.nanoTime() - start) / 1_000_000;
                List<String> all = new ArrayList<>(sender.lines());
                all.addAll(plugin.logs().since(mark));
                if (fileOffset >= 0) {
                    try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(logFile, "r")) {
                        raf.seek(fileOffset);
                        String line;
                        while ((line = raf.readLine()) != null) {
                            if (!line.isBlank()) all.add(line);
                        }
                    } catch (IOException e) {
                        plugin.getLogger().warning("exec log capture failed: " + e.getMessage());
                    }
                }
                future.complete(new ExecResult(okFinal, all, elapsed));
            }, 6);
        });
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!future.isDone()) {
                future.completeExceptionally(new CreeperError(Protocol.ERR_TIMEOUT,
                        "exec timed out after " + timeoutSeconds + "s"));
            }
        }, timeoutSeconds * 20L);
        return future;
    }

    private final class CapturingSender {
        private final List<String> lines = new ArrayList<>();

        private CommandSender asSender() {
            InvocationHandler handler = (Object proxy, Method method, Object[] args) -> {
                switch (method.getName()) {
                    case "sendMessage" -> {
                        if (args == null) {
                            return null;
                        }
                        if (args[0] instanceof String s) {
                            lines.add(s);
                        } else if (args[0] instanceof String[] arr) {
                            for (String s : arr) lines.add(s);
                        } else if (args[0] instanceof Component comp) {
                            lines.add(PlainTextComponentSerializer.plainText().serialize(comp));
                        }
                        return null;
                    }
                    case "getName" -> {
                        return "CreeperCLI-Console";
                    }
                    case "isOp" -> {
                        return true;
                    }
                    case "hasPermission", "isPermissionSet" -> {
                        return true;
                    }
                    case "getServer" -> {
                        return Bukkit.getServer();
                    }
                    case "getPermissionMessage" -> {
                        return null;
                    }
                    default -> {
                        return defaultReturn(method.getReturnType());
                    }
                }
            };
            return (CommandSender) Proxy.newProxyInstance(
                    Server.class.getClassLoader(),
                    new Class<?>[]{CommandSender.class, ConsoleCommandSender.class},
                    handler);
        }

        private static Object defaultReturn(Class<?> type) {
            if (!type.isPrimitive()) return null;
            if (type == boolean.class) return false;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == double.class) return 0.0;
            if (type == float.class) return 0.0f;
            if (type == short.class) return (short) 0;
            if (type == byte.class) return (byte) 0;
            if (type == char.class) return (char) 0;
            return null;
        }

        private List<String> lines() {
            return lines;
        }
    }
}
