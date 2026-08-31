

package dev.demonzdevelopment.creepercli.commands;

import com.google.gson.JsonObject;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.CreeperError;
import dev.demonzdevelopment.creepercli.Hashes;
import dev.demonzdevelopment.creepercli.Json;
import dev.demonzdevelopment.creepercli.Protocol;
import dev.demonzdevelopment.creepercli.net.ClientConnection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class EditCommands {
    private final CreeperCLIPlugin plugin;

    public EditCommands(CreeperCLIPlugin plugin) {
        this.plugin = plugin;
    }

    public JsonObject lock(ClientConnection conn, JsonObject params) throws CreeperError {
        String path = Json.opt(params, "path", null);
        if (path == null) throw new CreeperError(Protocol.ERR_INVALID_PARAMS, "path is required");
        Path p = conn.resolve(path);
        if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
            throw new CreeperError(Protocol.ERR_IS_DIRECTORY, "Cannot edit a directory");
        }
        var lock = plugin.locks().acquire(conn.session(), p);
        JsonObject res = new JsonObject();
        res.addProperty("path", plugin.sanitizer().toJailPath(p));
        res.addProperty("ttlSeconds", plugin.locks().ttlMillis() / 1000);
        res.addProperty("expiresAt", lock.expiresAt());
        return res;
    }

    public JsonObject push(ClientConnection conn, JsonObject params) throws CreeperError {
        String path = Json.opt(params, "path", null);
        if (path == null) throw new CreeperError(Protocol.ERR_INVALID_PARAMS, "path is required");
        Path p = conn.resolve(path);
        plugin.locks().renew(conn.session(), p);
        String content = Json.opt(params, "content", "");
        String clientSha = Json.opt(params, "sha256", null);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String actualSha = Hashes.sha256Hex(bytes);
        if (clientSha != null && !actualSha.equalsIgnoreCase(clientSha)) {
            throw new CreeperError(Protocol.ERR_CHECKSUM_MISMATCH, "Content hash mismatch");
        }
        try {
            Files.createDirectories(p.getParent());
            Path tmp = p.resolveSibling("." + p.getFileName() + ".creepercli-edit");
            Files.write(tmp, bytes, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new CreeperError(Protocol.ERR_IO, e.getMessage() == null ? "Write failed" : e.getMessage());
        }
        JsonObject res = Json.ok();
        res.addProperty("path", plugin.sanitizer().toJailPath(p));
        res.addProperty("bytes", bytes.length);
        res.addProperty("sha256", actualSha);
        return res;
    }

    public JsonObject unlock(ClientConnection conn, JsonObject params) throws CreeperError {
        String path = Json.opt(params, "path", null);
        if (path == null) throw new CreeperError(Protocol.ERR_INVALID_PARAMS, "path is required");
        Path p = conn.resolve(path);
        plugin.locks().release(conn.session(), p);
        return Json.ok();
    }
}
