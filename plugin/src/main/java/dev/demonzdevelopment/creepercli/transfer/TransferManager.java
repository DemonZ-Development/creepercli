

package dev.demonzdevelopment.creepercli.transfer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.CreeperError;
import dev.demonzdevelopment.creepercli.Hashes;
import dev.demonzdevelopment.creepercli.Json;
import dev.demonzdevelopment.creepercli.Protocol;
import dev.demonzdevelopment.creepercli.net.ClientConnection;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class TransferManager {
    private static final int MAX_LIST_ENTRIES = 100_000;
    private static final long TRANSFER_IDLE_TIMEOUT_MILLIS = 15 * 60_000L;

    private static final class PushState {
        final String token;
        final Path target;
        final Path temp;
        final FileChannel channel;
        final long expectedSize;
        final String expectedSha256;
        long received;
        volatile long lastActivity;
        final MessageDigest digest;

        PushState(String token, Path target, Path temp, FileChannel channel, long expectedSize,
                  String expectedSha256, MessageDigest digest) {
            this.token = token;
            this.target = target;
            this.temp = temp;
            this.channel = channel;
            this.expectedSize = expectedSize;
            this.expectedSha256 = expectedSha256;
            this.lastActivity = System.currentTimeMillis();
            this.digest = digest;
        }
    }

    private static final class PullState {
        final String token;
        final Path path;
        final long size;
        final String sha256;
        final FileChannel channel;
        volatile long lastActivity;

        PullState(String token, Path path, long size, String sha256, FileChannel channel) {
            this.token = token;
            this.path = path;
            this.size = size;
            this.sha256 = sha256;
            this.channel = channel;
            this.lastActivity = System.currentTimeMillis();
        }
    }

    private final CreeperCLIPlugin plugin;
    private final Map<String, PushState> pushes = new ConcurrentHashMap<>();
    private final Map<String, PullState> pulls = new ConcurrentHashMap<>();

    public TransferManager(CreeperCLIPlugin plugin) {
        this.plugin = plugin;
    }

    public JsonObject pushStart(ClientConnection conn, JsonObject params) throws CreeperError {
        String path = Json.opt(params, "path", null);
        long size = Json.optLong(params, "size", -1);
        String sha256 = Json.opt(params, "sha256", null);
        boolean force = Json.optBool(params, "force", false);
        if (path == null) throw new CreeperError(Protocol.ERR_INVALID_PARAMS, "path is required");
        if (size < 0 || size > plugin.cfg().maxTransferBytes()) {
            throw new CreeperError(Protocol.ERR_PAYLOAD_TOO_LARGE, "File too large (max " + plugin.cfg().maxTransferBytes() + " bytes)");
        }
        if (sha256 == null || !sha256.matches("(?i)[0-9a-f]{64}")) {
            throw new CreeperError(Protocol.ERR_INVALID_PARAMS, "sha256 must be a 64-character hexadecimal digest");
        }
        Path target = conn.resolve(path);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (!force) {
                throw new CreeperError(Protocol.ERR_ALREADY_EXISTS, "Remote file exists (pass force to overwrite)");
            }
            if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new CreeperError(Protocol.ERR_IS_DIRECTORY, "Remote path is a directory");
            }
        }
        Path parent = target.getParent();
        if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new CreeperError(Protocol.ERR_NOT_FOUND, "Remote parent directory does not exist");
        }
        Path temp = parent.resolve("." + target.getFileName() + ".creepercli-part-" + UUID.randomUUID());
        try {
            FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            String id = UUID.randomUUID().toString();
            PushState state = new PushState(conn.session().token, target, temp, channel, size, sha256,
                    MessageDigest.getInstance("SHA-256"));
            pushes.put(id, state);
            JsonObject res = new JsonObject();
            res.addProperty("transferId", id);
            res.addProperty("chunkSize", plugin.cfg().transferChunkSize());
            res.addProperty("target", conn.jailPath(target));
            return res;
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new CreeperError(Protocol.ERR_IO, "Cannot start upload: " + e.getMessage());
        }
    }

    public JsonObject pushChunk(ClientConnection conn, JsonObject params) throws CreeperError {
        String id = Json.opt(params, "transferId", null);
        int index = Json.optInt(params, "index", -1);
        String data64 = Json.opt(params, "data", null);
        PushState state = pushState(conn, id);
        synchronized (state) {
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(data64 == null ? "" : data64);
            } catch (IllegalArgumentException e) {
                throw new CreeperError(Protocol.ERR_BAD_REQUEST, "Invalid base64 chunk");
            }
            int chunkSize = plugin.cfg().transferChunkSize();
            if (bytes.length > chunkSize) {
                abortPush(id);
                throw new CreeperError(Protocol.ERR_PAYLOAD_TOO_LARGE, "Chunk exceeds chunk size");
            }
            if (index < 0 || index * (long) chunkSize != state.received) {
                abortPush(id);
                throw new CreeperError(Protocol.ERR_BAD_REQUEST, "Chunk out of order (expected index " + (state.received / chunkSize) + ")");
            }
            try {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) state.channel.write(buffer);
            } catch (IOException e) {
                abortPush(id);
                throw new CreeperError(Protocol.ERR_IO, "Upload write failed: " + e.getMessage());
            }
            state.digest.update(bytes);
            state.received += bytes.length;
            state.lastActivity = System.currentTimeMillis();
            if (state.received > state.expectedSize) {
                abortPush(id);
                throw new CreeperError(Protocol.ERR_PAYLOAD_TOO_LARGE, "Upload exceeds declared size");
            }
            JsonObject res = new JsonObject();
            res.addProperty("received", state.received);
            return res;
        }
    }

    public JsonObject pushFinish(ClientConnection conn, JsonObject params) throws CreeperError {
        String id = Json.opt(params, "transferId", null);
        String clientSha = Json.opt(params, "sha256", null);
        PushState state = pushState(conn, id);
        synchronized (state) {
            if (state.received != state.expectedSize) {
                abortPush(id);
                throw new CreeperError(Protocol.ERR_PAYLOAD_TOO_LARGE, "Upload size mismatch: expected " + state.expectedSize + ", got " + state.received);
            }
            String serverSha;
            try {
                state.channel.force(true);
                state.channel.close();
                serverSha = java.util.HexFormat.of().formatHex(state.digest.digest());
            } catch (IOException e) {
                abortPush(id);
                throw new CreeperError(Protocol.ERR_IO, "Upload finalize failed: " + e.getMessage());
            }
            if (clientSha == null || !serverSha.equalsIgnoreCase(clientSha)
                    || !serverSha.equalsIgnoreCase(state.expectedSha256)) {
                abortPush(id);
                throw new CreeperError(Protocol.ERR_CHECKSUM_MISMATCH,
                        "Checksum mismatch: server " + serverSha + ", expected " + state.expectedSha256);
            }
            try {
                try {
                    Files.move(state.temp, state.target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(state.temp, state.target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                abortPush(id);
                throw new CreeperError(Protocol.ERR_IO, "Finalize move failed: " + e.getMessage());
            }
            pushes.remove(id, state);
            JsonObject res = new JsonObject();
            res.addProperty("target", conn.jailPath(state.target));
            res.addProperty("size", state.expectedSize);
            res.addProperty("sha256", serverSha);
            return res;
        }
    }

    public JsonObject pullStart(ClientConnection conn, JsonObject params) throws CreeperError {
        String path = Json.opt(params, "path", null);
        if (path == null) throw new CreeperError(Protocol.ERR_INVALID_PARAMS, "path is required");
        Path p = conn.resolve(path);
        if (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
            throw new CreeperError(Protocol.ERR_NOT_FOUND, "No such file: " + path);
        }
        long size;
        try {
            size = Files.size(p);
        } catch (IOException e) {
            throw new CreeperError(Protocol.ERR_IO, e.getMessage());
        }
        if (size > plugin.cfg().maxTransferBytes()) {
            throw new CreeperError(Protocol.ERR_PAYLOAD_TOO_LARGE, "File too large to transfer");
        }
        String sha;
        try {
            sha = Hashes.sha256File(p);
        } catch (IOException e) {
            throw new CreeperError(Protocol.ERR_IO, e.getMessage());
        }
        try {
            FileChannel channel = FileChannel.open(p, StandardOpenOption.READ);
            String id = UUID.randomUUID().toString();
            pulls.put(id, new PullState(conn.session().token, p, size, sha, channel));
            JsonObject res = new JsonObject();
            res.addProperty("transferId", id);
            res.addProperty("size", size);
            res.addProperty("sha256", sha);
            res.addProperty("chunkSize", plugin.cfg().transferChunkSize());
            res.addProperty("path", conn.jailPath(p));
            return res;
        } catch (IOException e) {
            throw new CreeperError(Protocol.ERR_IO, "Cannot open file for download: " + e.getMessage());
        }
    }

    public JsonObject pullChunk(ClientConnection conn, JsonObject params) throws CreeperError {
        String id = Json.opt(params, "transferId", null);
        long index = Json.optLong(params, "index", -1);
        PullState state = pullState(conn, id);
        if (index < 0) {
            throw new CreeperError(Protocol.ERR_INVALID_PARAMS, "index must be non-negative");
        }
        synchronized (state) {
            state.lastActivity = System.currentTimeMillis();
            int chunkSize = plugin.cfg().transferChunkSize();
            long pos = index * (long) chunkSize;
            JsonObject res = new JsonObject();
            if (pos >= state.size) {
                res.addProperty("index", index);
                res.addProperty("data", "");
                res.addProperty("eof", true);
                return res;
            }
            int len = (int) Math.min(chunkSize, state.size - pos);
            ByteBuffer buf = ByteBuffer.allocate(len);
            try {
                int n = state.channel.read(buf, pos);
                if (n < 0) n = 0;
                buf.flip();
                byte[] data = new byte[n];
                buf.get(data);
                res.addProperty("index", index);
                res.addProperty("data", Base64.getEncoder().encodeToString(data));
                res.addProperty("eof", pos + n >= state.size);
                return res;
            } catch (IOException e) {
                throw new CreeperError(Protocol.ERR_IO, "Read failed: " + e.getMessage());
            }
        }
    }

    public JsonObject pullFinish(ClientConnection conn, JsonObject params) throws CreeperError {
        String id = Json.opt(params, "transferId", null);
        String clientSha = Json.opt(params, "sha256", null);
        PullState state = pullState(conn, id);
        synchronized (state) {
            pulls.remove(id, state);
            try {
                state.channel.close();
            } catch (IOException ignored) {
            }
        }
        boolean ok = clientSha != null && state.sha256.equalsIgnoreCase(clientSha);
        JsonObject res = new JsonObject();
        res.addProperty("ok", ok);
        res.addProperty("sha256", state.sha256);
        if (!ok) {
            throw new CreeperError(Protocol.ERR_CHECKSUM_MISMATCH, "Checksum mismatch: server " + state.sha256 + ", client " + clientSha);
        }
        return res;
    }

    public JsonObject abort(ClientConnection conn, JsonObject params) {
        String id = Json.opt(params, "transferId", null);
        if (id != null) {
            PushState push = pushes.get(id);
            if (push != null && push.token.equals(conn.session().token)) abortPush(id);
            PullState pull = pulls.get(id);
            if (pull != null && pull.token.equals(conn.session().token)) abortPull(id);
        }
        return Json.ok();
    }

    public JsonObject list(ClientConnection conn, JsonObject params) throws CreeperError {
        String path = Json.opt(params, "path", ".");
        int maxDepth = Math.min(32, Math.max(1, Json.optInt(params, "maxDepth", 12)));
        Path p = conn.resolve(path);
        if (!Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
            throw new CreeperError(Protocol.ERR_NOT_DIRECTORY, "Not a directory: " + path);
        }
        JsonArray entries = new JsonArray();
        boolean[] truncated = {false};
        try (Stream<Path> walk = Files.walk(p, maxDepth)) {
            walk.sorted().forEach(f -> {
                if (truncated[0]) return;
                boolean isDir = Files.isDirectory(f, LinkOption.NOFOLLOW_LINKS);
                JsonObject e = new JsonObject();
                e.addProperty("path", plugin.sanitizer().toJailPath(f));
                e.addProperty("isDir", isDir);
                e.addProperty("size", isDir ? 0 : sizeOf(f));
                if (!isDir) {
                    String sha = null;
                    try {
                        sha = Hashes.sha256File(f);
                    } catch (IOException ignored) {
                    }
                    if (sha != null) e.addProperty("sha256", sha);
                }
                e.addProperty("mtime", mtimeOf(f));
                entries.add(e);
                if (entries.size() >= MAX_LIST_ENTRIES) truncated[0] = true;
            });
        } catch (IOException e) {
            throw new CreeperError(Protocol.ERR_IO, "Walk failed: " + e.getMessage());
        }
        JsonObject res = new JsonObject();
        res.add("entries", entries);
        res.addProperty("truncated", truncated[0]);
        return res;
    }

    public void abortByToken(String token) {
        for (String id : pushes.keySet()) {
            PushState st = pushes.get(id);
            if (st != null && st.token.equals(token)) abortPush(id);
        }
        for (String id : pulls.keySet()) {
            PullState st = pulls.get(id);
            if (st != null && st.token.equals(token)) abortPull(id);
        }
    }

    public void abortAll() {
        for (String id : new java.util.ArrayList<>(pushes.keySet())) abortPush(id);
        for (String id : new java.util.ArrayList<>(pulls.keySet())) abortPull(id);
    }

    public void sweep() {
        long cutoff = System.currentTimeMillis() - TRANSFER_IDLE_TIMEOUT_MILLIS;
        for (Map.Entry<String, PushState> entry : pushes.entrySet()) {
            if (entry.getValue().lastActivity < cutoff) abortPush(entry.getKey());
        }
        for (Map.Entry<String, PullState> entry : pulls.entrySet()) {
            if (entry.getValue().lastActivity < cutoff) abortPull(entry.getKey());
        }
    }

    private PushState pushState(ClientConnection conn, String id) throws CreeperError {
        if (id == null) throw new CreeperError(Protocol.ERR_NO_TRANSFER, "No transfer id");
        PushState state = pushes.get(id);
        if (state == null || !state.token.equals(conn.session().token)) {
            throw new CreeperError(Protocol.ERR_NO_TRANSFER, "Unknown or expired transfer");
        }
        return state;
    }

    private PullState pullState(ClientConnection conn, String id) throws CreeperError {
        if (id == null) throw new CreeperError(Protocol.ERR_NO_TRANSFER, "No transfer id");
        PullState state = pulls.get(id);
        if (state == null || !state.token.equals(conn.session().token)) {
            throw new CreeperError(Protocol.ERR_NO_TRANSFER, "Unknown or expired transfer");
        }
        return state;
    }

    private void abortPush(String id) {
        PushState state = pushes.remove(id);
        if (state == null) return;
        synchronized (state) {
            try {
                state.channel.close();
            } catch (IOException ignored) {
            }
            try {
                Files.deleteIfExists(state.temp);
            } catch (IOException ignored) {
            }
        }
    }

    private void abortPull(String id) {
        PullState state = pulls.remove(id);
        if (state == null) return;
        synchronized (state) {
            try {
                state.channel.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static long sizeOf(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0;
        }
    }

    private static long mtimeOf(Path p) {
        try {
            return Files.getLastModifiedTime(p, LinkOption.NOFOLLOW_LINKS).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }
}
