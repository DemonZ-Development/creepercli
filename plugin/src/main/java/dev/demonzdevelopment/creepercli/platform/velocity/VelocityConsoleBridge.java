

package dev.demonzdevelopment.creepercli.platform.velocity;

import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.CreeperError;
import dev.demonzdevelopment.creepercli.Protocol;
import com.velocitypowered.api.proxy.ProxyServer;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


public final class VelocityConsoleBridge implements dev.demonzdevelopment.creepercli.platform.ConsoleBridge {
    private final ProxyServer server;
    private final CreeperCLIPlugin core;

    public VelocityConsoleBridge(ProxyServer server, CreeperCLIPlugin core) {
        this.server = server;
        this.core = core;
    }

    public CompletableFuture<Result> exec(String command, int timeoutSeconds) {
        CompletableFuture<Result> future = new CompletableFuture<>();
        long mark = core.logs().mark();
        File logFile = new File(core.cfg().serverRoot().toFile(), "logs/latest.log");
        long fileOffset = logFile.isFile() ? logFile.length() : -1;
        long start = System.nanoTime();
        try {
            server.getCommandManager().executeAsync(server.getConsoleCommandSource(), command)
                    .whenComplete((ok, err) -> CompletableFuture.delayedExecutor(300, TimeUnit.MILLISECONDS)
                            .execute(() -> finish(future, ok, err, mark, logFile, fileOffset, start)));
        } catch (Throwable t) {
            core.getLogger().warning("exec threw: " + t);
            future.completeExceptionally(new CreeperError(Protocol.ERR_INTERNAL,
                    "Command execution failed: " + t.getMessage()));
        }
        CompletableFuture.delayedExecutor(timeoutSeconds, TimeUnit.SECONDS).execute(() -> {
            if (!future.isDone()) {
                future.completeExceptionally(new CreeperError(Protocol.ERR_TIMEOUT,
                        "exec timed out after " + timeoutSeconds + "s"));
            }
        });
        return future;
    }

    private void finish(CompletableFuture<Result> future, Boolean ok, Throwable err,
                        long mark, File logFile, long fileOffset, long start) {
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
    }
}
