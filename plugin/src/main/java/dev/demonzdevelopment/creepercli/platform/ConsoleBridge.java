

package dev.demonzdevelopment.creepercli.platform;

import java.util.List;
import java.util.concurrent.CompletableFuture;


public interface ConsoleBridge {

    record Result(boolean success, List<String> lines, long elapsedMs) {
    }

    CompletableFuture<Result> exec(String command, int timeoutSeconds);
}
