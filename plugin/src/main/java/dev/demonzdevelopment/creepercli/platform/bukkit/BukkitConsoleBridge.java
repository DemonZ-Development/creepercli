

package dev.demonzdevelopment.creepercli.platform.bukkit;

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


public final class BukkitConsoleBridge implements dev.demonzdevelopment.creepercli.platform.ConsoleBridge {
    private final BukkitLoader loader;
    private final CreeperCLIPlugin core;

    public BukkitConsoleBridge(BukkitLoader loader, CreeperCLIPlugin core) {
        this.loader = loader;
        this.core = core;
    }

    public CompletableFuture<Result> exec(String command, int timeoutSeconds) {
        CompletableFuture<Result> future = new CompletableFuture<>();
        CapturingSender sender = new CapturingSender();
        long mark = core.logs().mark();
        java.io.File logFile = new java.io.File(core.cfg().serverRoot().toFile(), "logs/latest.log");
        long fileOffset = logFile.isFile() ? logFile.length() : -1;
        long start = System.nanoTime();
        Bukkit.getScheduler().runTask(loader, () -> {
            boolean ok;
            try {
                ok = Bukkit.getServer().dispatchCommand(sender.asSender(), command);
            } catch (Throwable t) {
                core.getLogger().warning("exec threw on main thread: " + t);
                future.completeExceptionally(new CreeperError(Protocol.ERR_INTERNAL, "Command execution failed: " + t.getMessage()));
                return;
            }
            boolean okFinal = ok;
            Bukkit.getScheduler().runTaskLaterAsynchronously(loader, () -> {
                long elapsed = (System.nanoTime() - start) / 1_000_000;
                List<String> all = new ArrayList<>(sender.lines());
                all.addAll(core.logs().since(mark));
                if (fileOffset >= 0) {
                    try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(logFile, "r")) {
                        raf.seek(fileOffset);
                        String line;
                        while ((line = raf.readLine()) != null) {
                            if (!line.isBlank()) all.add(line);
                        }
                    } catch (IOException e) {
                        core.getLogger().warning("exec log capture failed: " + e.getMessage());
                    }
                }
                future.complete(new Result(okFinal, all, elapsed));
            }, 6);
        });
        Bukkit.getScheduler().runTaskLater(loader, () -> {
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
                        capture(args);
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

        
        
        private void capture(Object[] args) {
            if (args == null) {
                return;
            }
            try {
                if (args[0] instanceof String s) {
                    lines.add(s);
                } else if (args[0] instanceof String[] arr) {
                    for (String s : arr) lines.add(s);
                } else if (args[0] instanceof Component comp) {
                    lines.add(PlainTextComponentSerializer.plainText().serialize(comp));
                }
            } catch (NoClassDefFoundError ignored) {
            }
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
