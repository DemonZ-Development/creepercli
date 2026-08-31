

package dev.demonzdevelopment.creepercli.api;

import com.google.gson.JsonObject;
import dev.demonzdevelopment.creepercli.CreeperError;
import dev.demonzdevelopment.creepercli.net.ClientConnection;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface ActionHandler {
    CompletableFuture<JsonObject> handle(ClientConnection connection, JsonObject params) throws CreeperError;
}
