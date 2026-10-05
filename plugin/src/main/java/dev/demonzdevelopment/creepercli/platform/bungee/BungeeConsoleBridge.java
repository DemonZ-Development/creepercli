

package dev.demonzdevelopment.creepercli.platform.bungee;

import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.CreeperError;
import dev.demonzdevelopment.creepercli.Protocol;
import net.md_5.bungee.api.ProxyServer;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


public final class BungeeConsoleBridge implements dev.demonzdevelopment.creepercli.platform.ConsoleBridge {
    private final CreeperCLIPlugin core;

    public BungeeConsoleBridge(CreeperCLIPlugin core) {
        this.core = core;
    }

    public CompletableFuture<Result> exec(String command, int timeoutSeconds) {
        CompletableFuture<Result> future = new CompletableFuture<>();
        long mark = core.logs().mark();
        File logFile = new File(core.cfg().serverRoot().toFile(), "logs/latest.log");
        long fileOffset = logFile.isFile() ? logFile.length() : -1;
        long start = System.nanoTime();
        CompletableFuture<Boolean> dispatched = new CompletableFuture<>();
        CompletableFuture.supplyAsync(() -> {
                    try {
                        return ProxyServer.getInstance().getPluginManager()
                                .dispatchCommand(ProxyServer.getInstance().getConsole(), command);
                    } catch (Throwable t) {
                        core.getLogger().warning("exec threw: " + t);
                        return false;
                    }
                })
                .whenComplete((ok, err) -> {
                    if (err != null) dispatched.completeExceptionally(err);
                    else dispatched.complete(Boolean.TRUE.equals(ok));
                });
        
        dispatched.whenComplete((ok, err) -> CompletableFuture.delayedExecutor(300, TimeUnit.MILLISECONDS).execute(() -> {
            if (err != null) {
                future.completeExceptionally(new CreeperError(Protocol.ERR_INTERNAL,
                        "Command execution failed: " + err.getMessage()));
                return;
            }
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            List<String> all = new ArrayList<>(core.logs().since(mark));
            if (fileOffset >= 0) {
                try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
                    raf.seek(fileOffset);
                    String line;
                    while ((line = raf.readLine()) != null) {
                        if (!line.isBlank()) all.add(line);
                    }
                } catch (IOException e) {
                    core.getLogger().warning("exec log capture failed: " + e.getMessage());
                }
            }
            future.complete(new Result(Boolean.TRUE.equals(ok), all, elapsed));
        }));
        CompletableFuture.delayedExecutor(timeoutSeconds, TimeUnit.SECONDS).execute(() -> {
            if (!future.isDone()) {
                future.completeExceptionally(new CreeperError(Protocol.ERR_TIMEOUT,
                        "exec timed out after " + timeoutSeconds + "s"));
            }
        });
        return future;
    }
}
