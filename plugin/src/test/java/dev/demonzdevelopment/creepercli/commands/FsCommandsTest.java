package dev.demonzdevelopment.creepercli.commands;

import com.google.gson.JsonObject;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.CreeperError;
import dev.demonzdevelopment.creepercli.Protocol;
import dev.demonzdevelopment.creepercli.net.ClientConnection;
import dev.demonzdevelopment.creepercli.sandbox.PathSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FsCommandsTest {
    @TempDir
    Path root;

    private FsCommands commands;
    private ClientConnection connection;

    @BeforeEach
    void setUp() throws Exception {
        PathSanitizer sanitizer = new PathSanitizer(root);
        CreeperCLIPlugin plugin = mock(CreeperCLIPlugin.class);
        when(plugin.sanitizer()).thenReturn(sanitizer);
        connection = mock(ClientConnection.class);
        doAnswer(inv -> sanitizer.resolve("", inv.getArgument(0))).when(connection).resolve(any());
        commands = new FsCommands(plugin);
    }

    @Test
    void tailHandlesTrailingNewline() throws Exception {
        Files.writeString(root.resolve("log.txt"), "first\nsecond\n");
        JsonObject params = new JsonObject();
        params.addProperty("path", "/log.txt");
        params.addProperty("lines", 1);

        JsonObject result = commands.tail(connection, params);

        assertEquals(1, result.getAsJsonArray("lines").size());
        assertEquals("second", result.getAsJsonArray("lines").get(0).getAsString());
        assertTrue(result.get("truncated").getAsBoolean());
    }

    @Test
    void recursiveCopyRejectsDestinationInsideSource() throws Exception {
        Files.createDirectories(root.resolve("source"));
        Files.writeString(root.resolve("source/a.txt"), "a");
        JsonObject params = new JsonObject();
        params.addProperty("src", "/source");
        params.addProperty("dst", "/source/nested");
        params.addProperty("recursive", true);

        CreeperError error = assertThrows(CreeperError.class, () -> commands.cp(connection, params));
        assertEquals(Protocol.ERR_INVALID_PARAMS, error.code());
    }
}
