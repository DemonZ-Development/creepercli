

package dev.demonzdevelopment.creepercli.commands;

import com.google.gson.JsonObject;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.CreeperError;
import dev.demonzdevelopment.creepercli.Json;
import dev.demonzdevelopment.creepercli.Protocol;
import dev.demonzdevelopment.creepercli.net.ClientConnection;
import dev.demonzdevelopment.creepercli.transfer.TransferManager;

public final class TransferCommands {
    private final CreeperCLIPlugin plugin;

    public TransferCommands(CreeperCLIPlugin plugin) {
        this.plugin = plugin;
    }

    private TransferManager manager() {
        return plugin.transfers();
    }

    public JsonObject pushStart(ClientConnection conn, JsonObject params) throws CreeperError {
        return manager().pushStart(conn, params);
    }

    public JsonObject pushChunk(ClientConnection conn, JsonObject params) throws CreeperError {
        return manager().pushChunk(conn, params);
    }

    public JsonObject pushFinish(ClientConnection conn, JsonObject params) throws CreeperError {
        return manager().pushFinish(conn, params);
    }

    public JsonObject pullStart(ClientConnection conn, JsonObject params) throws CreeperError {
        return manager().pullStart(conn, params);
    }

    public JsonObject pullChunk(ClientConnection conn, JsonObject params) throws CreeperError {
        return manager().pullChunk(conn, params);
    }

    public JsonObject pullFinish(ClientConnection conn, JsonObject params) throws CreeperError {
        return manager().pullFinish(conn, params);
    }

    public JsonObject abort(ClientConnection conn, JsonObject params) throws CreeperError {
        return manager().abort(conn, params);
    }

    public JsonObject list(ClientConnection conn, JsonObject params) throws CreeperError {
        return manager().list(conn, params);
    }
}
