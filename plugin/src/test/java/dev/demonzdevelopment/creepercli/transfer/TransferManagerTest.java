

package dev.demonzdevelopment.creepercli.transfer;

import com.google.gson.JsonObject;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.CreeperError;
import dev.demonzdevelopment.creepercli.PluginConfig;
import dev.demonzdevelopment.creepercli.net.ClientConnection;
import dev.demonzdevelopment.creepercli.net.Session;
import dev.demonzdevelopment.creepercli.sandbox.PathSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransferManagerTest {

    @TempDir
    Path tempRoot;

    private CreeperCLIPlugin plugin;
    private PluginConfig cfg;
    private PathSanitizer sanitizer;
    private TransferManager transfers;
    private ClientConnection conn;
    private Session session;

    @BeforeEach
    void setUp() throws Exception {
        plugin = mock(CreeperCLIPlugin.class);
        Logger logger = Logger.getLogger("TransferManagerTest");
        when(plugin.getLogger()).thenReturn(logger);
        cfg = mock(PluginConfig.class);
        when(cfg.transferChunkSize()).thenReturn(1024);
        when(cfg.maxTransferBytes()).thenReturn(10L * 1024 * 1024);
        when(plugin.cfg()).thenReturn(cfg);
        sanitizer = new PathSanitizer(tempRoot);
        when(plugin.sanitizer()).thenReturn(sanitizer);

        transfers = new TransferManager(plugin);

        
        session = new Session("test_token", "admin", "127.0.0.1", new dev.demonzdevelopment.creepercli.security.RateLimiter(100, 100));
        conn = mock(ClientConnection.class);
        when(conn.session()).thenReturn(session);
        when(conn.remoteIp()).thenReturn("127.0.0.1");
        doAnswer(inv -> sanitizer.resolve("", inv.getArgument(0)))
                .when(conn).resolve(ArgumentMatchers.<String>any());
        doAnswer(inv -> sanitizer.toJailPath(inv.getArgument(0)))
                .when(conn).jailPath(any());
    }

    @Test
    void pushAndPullRoundTripSucceeds() throws Exception {
        byte[] data = "Hello CreeperCLI world\nThis is line two".getBytes();
        Path target = tempRoot.resolve("roundtrip.txt");
        Files.createDirectories(tempRoot);

        
        JsonObject startParams = new JsonObject();
        startParams.addProperty("path", "/roundtrip.txt");
        startParams.addProperty("size", data.length);
        String sha = sha256Hex(data);
        startParams.addProperty("sha256", sha);
        JsonObject start = transfers.pushStart(conn, startParams);
        String id = start.get("transferId").getAsString();

        
        int sent = 0;
        int idx = 0;
        int chunkSize = cfg.transferChunkSize();
        while (sent < data.length) {
            int n = Math.min(chunkSize, data.length - sent);
            JsonObject chunk = new JsonObject();
            chunk.addProperty("transferId", id);
            chunk.addProperty("index", idx++);
            chunk.addProperty("data", Base64.getEncoder().encodeToString(java.util.Arrays.copyOfRange(data, sent, sent + n)));
            transfers.pushChunk(conn, chunk);
            sent += n;
        }

        
        JsonObject finish = new JsonObject();
        finish.addProperty("transferId", id);
        finish.addProperty("sha256", sha);
        JsonObject finRes = transfers.pushFinish(conn, finish);
        assertEquals(target.toRealPath(), Files.exists(target) ? target.toRealPath() : target);
        assertTrue(Files.exists(target));
        byte[] read = Files.readAllBytes(target);
        assertEquals(data.length, read.length);
        assertEquals(sha, sha256Hex(read));
        assertEquals(sha, finRes.get("sha256").getAsString());

        
        JsonObject pullStart = new JsonObject();
        pullStart.addProperty("path", "/roundtrip.txt");
        JsonObject pStart = transfers.pullStart(conn, pullStart);
        String pullId = pStart.get("transferId").getAsString();
        long pSize = pStart.get("size").getAsLong();
        assertEquals(data.length, pSize);
        assertEquals(sha, pStart.get("sha256").getAsString());

        long idx2 = 0;
        while (true) {
            JsonObject req = new JsonObject();
            req.addProperty("transferId", pullId);
            req.addProperty("index", idx2++);
            JsonObject res = transfers.pullChunk(conn, req);
            String b64 = res.get("data").getAsString();
            if (b64.isEmpty()) break;
            byte[] got = Base64.getDecoder().decode(b64);
            
            int off = (int) ((idx2 - 1) * chunkSize);
            for (int i = 0; i < got.length; i++) {
                assertEquals(data[off + i], got[i]);
            }
            if (res.get("eof").getAsBoolean()) break;
        }

        JsonObject pullFin = new JsonObject();
        pullFin.addProperty("transferId", pullId);
        pullFin.addProperty("sha256", sha);
        JsonObject pullFinRes = transfers.pullFinish(conn, pullFin);
        assertTrue(pullFinRes.get("ok").getAsBoolean());
    }

    @Test
    void pushRejectedWhenSizeMismatchOnFinish() throws Exception {
        Path target = tempRoot.resolve("bad.bin");
        byte[] data = new byte[64];
        for (int i = 0; i < data.length; i++) data[i] = (byte) i;
        JsonObject startParams = new JsonObject();
        startParams.addProperty("path", "/bad.bin");
        startParams.addProperty("size", data.length);
        startParams.addProperty("sha256", sha256Hex(data));
        JsonObject start = transfers.pushStart(conn, startParams);
        String id = start.get("transferId").getAsString();

        
        JsonObject chunk = new JsonObject();
        chunk.addProperty("transferId", id);
        chunk.addProperty("index", 0);
        chunk.addProperty("data", Base64.getEncoder().encodeToString(java.util.Arrays.copyOfRange(data, 0, 32)));
        transfers.pushChunk(conn, chunk);

        JsonObject finish = new JsonObject();
        finish.addProperty("transferId", id);
        finish.addProperty("sha256", sha256Hex(data));
        CreeperError err = assertThrows(CreeperError.class, () -> transfers.pushFinish(conn, finish));
        assertEquals(dev.demonzdevelopment.creepercli.Protocol.ERR_PAYLOAD_TOO_LARGE, err.code());
        
        assertFalse(Files.exists(target));
    }

    @Test
    void pushOutOfOrderChunkRejected() throws Exception {
        JsonObject startParams = new JsonObject();
        startParams.addProperty("path", "/ooo.bin");
        startParams.addProperty("size", 8);
        startParams.addProperty("sha256", "ignored");
        JsonObject start = transfers.pushStart(conn, startParams);
        String id = start.get("transferId").getAsString();

        JsonObject chunk = new JsonObject();
        chunk.addProperty("transferId", id);
        chunk.addProperty("index", 5); 
        chunk.addProperty("data", Base64.getEncoder().encodeToString(new byte[8]));
        CreeperError err = assertThrows(CreeperError.class, () -> transfers.pushChunk(conn, chunk));
        assertEquals(dev.demonzdevelopment.creepercli.Protocol.ERR_BAD_REQUEST, err.code());
    }

    @Test
    void abortCleansUpPushTemp() throws Exception {
        byte[] data = new byte[64];
        JsonObject startParams = new JsonObject();
        startParams.addProperty("path", "/abort.bin");
        startParams.addProperty("size", data.length);
        startParams.addProperty("sha256", sha256Hex(data));
        JsonObject start = transfers.pushStart(conn, startParams);
        String id = start.get("transferId").getAsString();
        transfers.abort(conn, pushIdParams(id));
        assertFalse(Files.exists(tempRoot.resolve(".abort.bin.creepercli-part")));
    }

    @Test
    void listReturnsDirectoryContents() throws Exception {
        Files.writeString(tempRoot.resolve("a.txt"), "a");
        Files.createDirectories(tempRoot.resolve("sub"));
        Files.writeString(tempRoot.resolve("sub/b.txt"), "b");

        JsonObject params = new JsonObject();
        params.addProperty("path", "/");
        params.addProperty("maxDepth", 4);
        JsonObject res = transfers.list(conn, params);
        assertTrue(res.has("entries"));
        assertFalse(res.get("truncated").getAsBoolean());
    }

    @Test
    void abortAllClearsState() throws Exception {
        JsonObject startParams = new JsonObject();
        startParams.addProperty("path", "/clear.bin");
        startParams.addProperty("size", 8);
        startParams.addProperty("sha256", "ignored");
        transfers.pushStart(conn, startParams);
        transfers.abortAll();
        
        JsonObject startParams2 = new JsonObject();
        startParams2.addProperty("path", "/clear2.bin");
        startParams2.addProperty("size", 4);
        startParams2.addProperty("sha256", "ignored");
        JsonObject start2 = transfers.pushStart(conn, startParams2);
        assertNotNull(start2.get("transferId"));
    }

    private static JsonObject pushIdParams(String id) {
        JsonObject p = new JsonObject();
        p.addProperty("transferId", id);
        return p;
    }

    private static String sha256Hex(byte[] data) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(md.digest(data));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}