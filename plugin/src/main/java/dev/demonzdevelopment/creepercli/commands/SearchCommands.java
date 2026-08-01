package dev.demonzdevelopment.creepercli.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.CreeperError;
import dev.demonzdevelopment.creepercli.Json;
import dev.demonzdevelopment.creepercli.Protocol;
import dev.demonzdevelopment.creepercli.net.ClientConnection;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

public final class SearchCommands {
    private final CreeperCLIPlugin plugin;

    public SearchCommands(CreeperCLIPlugin plugin) {
        this.plugin = plugin;
    }

    public JsonObject grep(ClientConnection conn, JsonObject params) throws CreeperError {
        String path = Json.opt(params, "path", ".");
        String patternStr = Json.opt(params, "pattern", null);
        if (patternStr == null || patternStr.isEmpty()) {
            throw new CreeperError(Protocol.ERR_INVALID_PARAMS, "pattern is required");
        }
        boolean recursive = Json.optBool(params, "recursive", true);
        boolean caseInsensitive = Json.optBool(params, "caseInsensitive", false);
        boolean lineNumbers = Json.optBool(params, "lineNumbers", true);
        int maxDepth = clamp(Json.optInt(params, "maxDepth", 6), 1, 16);
        long maxFileBytes = Json.optLong(params, "maxFileBytes", 5L * 1024 * 1024);
        int maxResults = clamp(Json.optInt(params, "maxResults", 1000), 1, 10_000);

        Pattern pattern;
        try {
            pattern = Pattern.compile(patternStr, caseInsensitive ? Pattern.CASE_INSENSITIVE : 0);
        } catch (PatternSyntaxException e) {
            throw new CreeperError(Protocol.ERR_INVALID_PARAMS, "Invalid pattern: " + e.getMessage());
        }

        Path p = conn.resolve(path);
        if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
            throw new CreeperError(Protocol.ERR_NOT_FOUND, "No such file or directory: " + path);
        }

        List<Path> files = new ArrayList<>();
        if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
            files.add(p);
        } else if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
            try (Stream<Path> walk = Files.walk(p, recursive ? maxDepth : 1)) {
                walk.filter(f -> Files.isRegularFile(f, LinkOption.NOFOLLOW_LINKS)).forEach(files::add);
            } catch (IOException e) {
                throw new CreeperError(Protocol.ERR_IO, "Walk failed: " + e.getMessage());
            }
        } else {
            throw new CreeperError(Protocol.ERR_NOT_FOUND, "No such file or directory: " + path);
        }

        JsonArray matches = new JsonArray();
        int filesScanned = 0;
        boolean truncated = false;
        for (Path file : files) {
            if (truncated) break;
            try {
                if (Files.size(file) > maxFileBytes) continue;
            } catch (IOException e) {
                continue;
            }
            filesScanned++;
            try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                int n = 0;
                while ((line = r.readLine()) != null) {
                    n++;
                    if (!pattern.matcher(line).find()) continue;
                    JsonObject m = new JsonObject();
                    m.addProperty("path", plugin.sanitizer().toJailPath(file));
                    if (lineNumbers) m.addProperty("line", n);
                    m.addProperty("text", line);
                    matches.add(m);
                    if (matches.size() >= maxResults) {
                        truncated = true;
                        break;
                    }
                }
            } catch (IOException ignored) {
            }
        }

        JsonObject res = new JsonObject();
        res.add("matches", matches);
        res.addProperty("filesScanned", filesScanned);
        res.addProperty("truncated", truncated);
        return res;
    }

    public JsonObject find(ClientConnection conn, JsonObject params) throws CreeperError {
        String path = Json.opt(params, "path", ".");
        String glob = Json.opt(params, "glob", "*");
        int maxDepth = clamp(Json.optInt(params, "maxDepth", 12), 1, 32);
        int maxResults = clamp(Json.optInt(params, "maxResults", 5000), 1, 100_000);

        Path p = conn.resolve(path);
        if (!Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
            throw new CreeperError(Protocol.ERR_NOT_DIRECTORY, "Not a directory: " + path);
        }
        PathMatcher matcher;
        try {
            matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
        } catch (IllegalArgumentException e) {
            throw new CreeperError(Protocol.ERR_INVALID_PARAMS, "Invalid glob: " + e.getMessage());
        }
        final PathMatcher shallow = glob.startsWith("**/")
                ? FileSystems.getDefault().getPathMatcher("glob:" + glob.substring(3))
                : null;

        JsonArray results = new JsonArray();
        boolean[] truncated = {false};
        try (Stream<Path> walk = Files.walk(p, maxDepth)) {
            walk.filter(f -> Files.isRegularFile(f, LinkOption.NOFOLLOW_LINKS)).forEach(f -> {
                if (truncated[0]) return;
                Path rel = p.relativize(f);
                if (matcher.matches(rel) || matcher.matches(rel.getFileName())
                        || (shallow != null && shallow.matches(rel.getFileName()))) {
                    results.add(plugin.sanitizer().toJailPath(f));
                    if (results.size() >= maxResults) truncated[0] = true;
                }
            });
        } catch (IOException e) {
            throw new CreeperError(Protocol.ERR_IO, "Walk failed: " + e.getMessage());
        }

        JsonObject res = new JsonObject();
        res.add("results", results);
        res.addProperty("truncated", truncated[0]);
        return res;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
