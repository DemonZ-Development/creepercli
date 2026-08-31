

package dev.demonzdevelopment.creepercli.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.CreeperError;
import dev.demonzdevelopment.creepercli.Json;
import dev.demonzdevelopment.creepercli.Protocol;
import dev.demonzdevelopment.creepercli.net.ClientConnection;
import dev.demonzdevelopment.creepercli.net.Session;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.attribute.FileTime;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class FsCommands {
    private static final int MAX_CAT_BYTES = 1024 * 1024;

    private final CreeperCLIPlugin plugin;

    public FsCommands(CreeperCLIPlugin plugin) {
        this.plugin = plugin;
    }

    public JsonObject pwd(ClientConnection conn) {
        JsonObject res = new JsonObject();
        res.addProperty("cwd", Session.cwdString(conn.session()));
        return res;
    }

    public JsonObject ls(ClientConnection conn, JsonObject params) throws CreeperError {
        String path = Json.opt(params, "path", ".");
        boolean longFormat = Json.optBool(params, "long", false);
        boolean all = Json.optBool(params, "all", false);
        Path p = conn.resolve(path);
        JsonArray entries = new JsonArray();
        if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
            entries.add(entry(p, p.getFileName().toString(), longFormat));
        } else if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
            for (Path child : listChildren(p)) {
                String name = child.getFileName().toString();
                if (!all && name.startsWith(".")) continue;
                entries.add(entry(child, name, longFormat));
            }
        } else {
            throw new CreeperError(Protocol.ERR_NOT_FOUND, "No such file or directory: " + path);
        }
        JsonObject res = new JsonObject();
        res.add("entries", entries);
        res.addProperty("cwd", Session.cwdString(conn.session()));
        res.addProperty("path", plugin.sanitizer().toJailPath(p));
        return res;
    }

    public JsonObject cd(ClientConnection conn, JsonObject params) throws CreeperError {
        String path = Json.opt(params, "path", "/");
        Path p = conn.resolve(path);
        if (!Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
            throw new CreeperError(Protocol.ERR_NOT_DIRECTORY, "Not a directory: " + path);
        }
        Path root = plugin.sanitizer().root();
        conn.session().cwd = root.equals(p) ? "" : root.relativize(p).toString();
        JsonObject res = new JsonObject();
        res.addProperty("cwd", Session.cwdString(conn.session()));
        return res;
    }

    public JsonObject tree(ClientConnection conn, JsonObject params) throws CreeperError {
        String path = Json.opt(params, "path", ".");
        int depth = clamp(Json.optInt(params, "depth", 3), 0, 6);
        Path p = conn.resolve(path);
        if (!Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
            throw new CreeperError(Protocol.ERR_NOT_DIRECTORY, "Not a directory: " + path);
        }
        int[] counter = {0};
        JsonObject res = new JsonObject();
        res.add("children", treeChildren(p, depth, counter));
        res.addProperty("entries", counter[0]);
        res.addProperty("truncated", counter[0] >= 10000);
        res.addProperty("root", plugin.sanitizer().toJailPath(p));
        return res;
    }

    public JsonObject cat(ClientConnection conn, JsonObject params) throws CreeperError {
        String path = Json.opt(params, "path", null);
        if (path == null) throw new CreeperError(Protocol.ERR_INVALID_PARAMS, "path is required");
        Path p = conn.resolve(path);
        requireFile(p);
        long size;
        try {
            size = Files.size(p);
        } catch (IOException e) {
            throw io(e);
        }
        boolean truncated = size > MAX_CAT_BYTES;
        String content;
        if (truncated) {
            try (RandomAccessFile raf = new RandomAccessFile(p.toFile(), "r")) {
                byte[] buf = new byte[MAX_CAT_BYTES];
                raf.readFully(buf);
                content = new String(buf, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw io(e);
            }
        } else {
            try {
                content = Files.readString(p, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw io(e);
            }
        }
        JsonObject res = new JsonObject();
        res.addProperty("content", content);
        res.addProperty("size", size);
        res.addProperty("truncated", truncated);
        return res;
    }

    public JsonObject head(ClientConnection conn, JsonObject params) throws CreeperError {
        String path = Json.opt(params, "path", null);
        int lines = clamp(Json.optInt(params, "lines", 10), 1, 1000);
        if (path == null) throw new CreeperError(Protocol.ERR_INVALID_PARAMS, "path is required");
        Path p = conn.resolve(path);
        requireFile(p);
        JsonArray arr = new JsonArray();
        int count = 0;
        try (BufferedReader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String line;
            while (count < lines && (line = r.readLine()) != null) {
                arr.add(line);
                count++;
            }
        } catch (IOException e) {
            throw io(e);
        }
        JsonObject res = new JsonObject();
        res.add("lines", arr);
        res.addProperty("truncated", count == lines);
        return res;
    }

    public JsonObject tail(ClientConnection conn, JsonObject params) throws CreeperError {
        String path = Json.opt(params, "path", null);
        int lines = clamp(Json.optInt(params, "lines", 10), 1, 1000);
        if (path == null) throw new CreeperError(Protocol.ERR_INVALID_PARAMS, "path is required");
        Path p = conn.resolve(path);
        requireFile(p);
        JsonArray arr = new JsonArray();
        boolean truncated = false;
        try (RandomAccessFile raf = new RandomAccessFile(p.toFile(), "r")) {
            long fileSize = raf.length();
            if (fileSize > 0) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                int linesSeen = 0;
                long pos = fileSize - 1;
                while (pos >= 0) {
                    raf.seek(pos);
                    int c = raf.read();
                    bo.write(c);
                    if (c == '\n') {
                        linesSeen++;
                        if (linesSeen == lines) {
                            truncated = true;
                            break;
                        }
                    }
                    pos--;
                }
                byte[] bytes = bo.toByteArray();
                byte[] reversed = new byte[bytes.length];
                for (int i = 0; i < bytes.length; i++) {
                    reversed[bytes.length - 1 - i] = bytes[i];
                }
                String text = new String(reversed, StandardCharsets.UTF_8);
                String[] split = text.split("\\R", -1);
                for (int i = 0; i < split.length; i++) {
                    String part = split[i];
                    if (i == split.length - 1 && part.isEmpty()) continue;
                    arr.add(part);
                }
            }
        } catch (IOException e) {
            throw io(e);
        }
        JsonObject res = new JsonObject();
        res.add("lines", arr);
        res.addProperty("truncated", truncated);
        return res;
    }

    public JsonObject wc(ClientConnection conn, JsonObject params) throws CreeperError {
        String path = Json.opt(params, "path", null);
        if (path == null) throw new CreeperError(Protocol.ERR_INVALID_PARAMS, "path is required");
        Path p = conn.resolve(path);
        requireFile(p);
        int lines = 0;
        int words = 0;
        long chars = 0;
        try (BufferedReader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                lines++;
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    words += trimmed.split("\\s+").length;
                }
                chars += line.length() + 1;
            }
        } catch (IOException e) {
            throw io(e);
        }
        long bytes;
        try {
            bytes = Files.size(p);
        } catch (IOException e) {
            bytes = 0;
        }
        JsonObject res = new JsonObject();
        res.addProperty("lines", lines);
        res.addProperty("words", words);
        res.addProperty("chars", chars);
        res.addProperty("bytes", bytes);
        return res;
    }

    public JsonObject touch(ClientConnection conn, JsonObject params) throws CreeperError {
        String path = Json.opt(params, "path", null);
        if (path == null) throw new CreeperError(Protocol.ERR_INVALID_PARAMS, "path is required");
        Path p = conn.resolve(path);
        try {
            if (Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
                Files.setLastModifiedTime(p, FileTime.fromMillis(System.currentTimeMillis()));
            } else {
                Files.createFile(p);
            }
        } catch (IOException e) {
            throw io(e);
        }
        JsonObject res = Json.ok();
        res.addProperty("path", plugin.sanitizer().toJailPath(p));
        return res;
    }

    public JsonObject mkdir(ClientConnection conn, JsonObject params) throws CreeperError {
        String path = Json.opt(params, "path", null);
        boolean parents = Json.optBool(params, "parents", false);
        if (path == null) throw new CreeperError(Protocol.ERR_INVALID_PARAMS, "path is required");
        Path p = conn.resolve(path);
        if (Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
            throw new CreeperError(Protocol.ERR_ALREADY_EXISTS, "Already exists: " + path);
        }
        try {
            if (parents) {
                Files.createDirectories(p);
            } else {
                Files.createDirectory(p);
            }
        } catch (java.nio.file.FileAlreadyExistsException e) {
            throw new CreeperError(Protocol.ERR_ALREADY_EXISTS, "Already exists: " + path);
        } catch (IOException e) {
            throw io(e);
        }
        JsonObject res = Json.ok();
        res.addProperty("path", plugin.sanitizer().toJailPath(p));
        return res;
    }

    public JsonObject rm(ClientConnection conn, JsonObject params) throws CreeperError {
        String path = Json.opt(params, "path", null);
        boolean recursive = Json.optBool(params, "recursive", false);
        if (path == null) throw new CreeperError(Protocol.ERR_INVALID_PARAMS, "path is required");
        Path p = conn.resolve(path);
        if (p.equals(plugin.sanitizer().root())) {
            throw new CreeperError(Protocol.ERR_FORBIDDEN, "Cannot remove the jail root");
        }
        if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
            throw new CreeperError(Protocol.ERR_NOT_FOUND, "No such file or directory: " + path);
        }
        try {
            if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                if (!recursive) {
                    throw new CreeperError(Protocol.ERR_IS_DIRECTORY, "Is a directory (use recursive)");
                }
                deleteTree(p);
            } else {
                Files.delete(p);
            }
        } catch (CreeperError e) {
            throw e;
        } catch (IOException e) {
            throw io(e);
        }
        JsonObject res = Json.ok();
        res.addProperty("path", plugin.sanitizer().toJailPath(p));
        return res;
    }

    public JsonObject cp(ClientConnection conn, JsonObject params) throws CreeperError {
        String src = Json.opt(params, "src", null);
        String dst = Json.opt(params, "dst", null);
        boolean recursive = Json.optBool(params, "recursive", false);
        if (src == null || dst == null) {
            throw new CreeperError(Protocol.ERR_INVALID_PARAMS, "src and dst are required");
        }
        Path s = conn.resolve(src);
        Path d = conn.resolve(dst);
        if (!Files.exists(s, LinkOption.NOFOLLOW_LINKS)) {
            throw new CreeperError(Protocol.ERR_NOT_FOUND, "No such file or directory: " + src);
        }
        if (Files.isDirectory(d, LinkOption.NOFOLLOW_LINKS)) {
            d = d.resolve(s.getFileName());
        }
        try {
            if (Files.isDirectory(s, LinkOption.NOFOLLOW_LINKS)) {
                if (!recursive) {
                    throw new CreeperError(Protocol.ERR_IS_DIRECTORY, "src is a directory (use recursive)");
                }
                copyTree(s, d);
            } else {
                Files.createDirectories(d.getParent());
                Files.copy(s, d, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
        } catch (CreeperError e) {
            throw e;
        } catch (IOException e) {
            throw io(e);
        }
        JsonObject res = Json.ok();
        res.addProperty("src", plugin.sanitizer().toJailPath(s));
        res.addProperty("dst", plugin.sanitizer().toJailPath(d));
        return res;
    }

    public JsonObject mv(ClientConnection conn, JsonObject params) throws CreeperError {
        String src = Json.opt(params, "src", null);
        String dst = Json.opt(params, "dst", null);
        if (src == null || dst == null) {
            throw new CreeperError(Protocol.ERR_INVALID_PARAMS, "src and dst are required");
        }
        Path s = conn.resolve(src);
        Path d = conn.resolve(dst);
        if (!Files.exists(s, LinkOption.NOFOLLOW_LINKS)) {
            throw new CreeperError(Protocol.ERR_NOT_FOUND, "No such file or directory: " + src);
        }
        if (Files.isDirectory(d, LinkOption.NOFOLLOW_LINKS)) {
            d = d.resolve(s.getFileName());
        }
        try {
            Files.move(s, d, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw io(e);
        }
        JsonObject res = Json.ok();
        res.addProperty("src", plugin.sanitizer().toJailPath(s));
        res.addProperty("dst", plugin.sanitizer().toJailPath(d));
        return res;
    }

    public JsonObject info(ClientConnection conn, JsonObject params) throws CreeperError {
        String path = Json.opt(params, "path", null);
        if (path == null) throw new CreeperError(Protocol.ERR_INVALID_PARAMS, "path is required");
        Path p = conn.resolve(path);
        if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
            throw new CreeperError(Protocol.ERR_NOT_FOUND, "No such file or directory: " + path);
        }
        JsonObject res = entry(p, p.getFileName().toString(), true);
        res.addProperty("path", plugin.sanitizer().toJailPath(p));
        return res;
    }

    private JsonObject entry(Path p, String name, boolean longFormat) {
        JsonObject o = new JsonObject();
        boolean isDir = Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS);
        boolean isLink = Files.isSymbolicLink(p);
        o.addProperty("name", name);
        o.addProperty("isDir", isDir);
        o.addProperty("size", sizeOf(p));
        o.addProperty("mtime", mtimeOf(p));
        if (longFormat) {
            o.addProperty("perms", perms(p));
        }
        o.addProperty("link", isLink ? linkTarget(p) : null);
        return o;
    }

    private List<Path> listChildren(Path dir) {
        List<Path> out = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path child : ds) out.add(child);
        } catch (IOException e) {
            throw io(e);
        }
        out.sort(Comparator.comparing(a -> a.getFileName().toString()));
        return out;
    }

    private JsonArray treeChildren(Path dir, int depthLeft, int[] counter) {
        JsonArray arr = new JsonArray();
        for (Path child : listChildren(dir)) {
            if (counter[0] >= 10000) break;
            counter[0]++;
            boolean isDir = Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS);
            JsonObject o = new JsonObject();
            o.addProperty("name", child.getFileName().toString());
            o.addProperty("isDir", isDir);
            if (isDir && depthLeft > 0) {
                o.add("children", treeChildren(child, depthLeft - 1, counter));
            }
            arr.add(o);
        }
        return arr;
    }

    private void deleteTree(Path dir) throws IOException {
        Files.walkFileTree(dir, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                Files.delete(d);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    private void copyTree(Path src, Path dst) throws IOException {
        Files.walkFileTree(src, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(dst.resolve(src.relativize(dir)));
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                Files.copy(file, dst.resolve(src.relativize(file)),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    private static void requireFile(Path p) throws CreeperError {
        if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
            throw new CreeperError(Protocol.ERR_NOT_FOUND, "No such file: " + p);
        }
        if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
            throw new CreeperError(Protocol.ERR_IS_DIRECTORY, "Is a directory");
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

    private static String linkTarget(Path p) {
        try {
            return Files.readSymbolicLink(p).toString();
        } catch (IOException e) {
            return null;
        }
    }

    private static String perms(Path p) {
        char[] out = "----------".toCharArray();
        if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) out[0] = 'd';
        else if (Files.isSymbolicLink(p)) out[0] = 'l';
        try {
            Set<PosixFilePermission> s = Files.getPosixFilePermissions(p, LinkOption.NOFOLLOW_LINKS);
            set(out, 1, s, PosixFilePermission.OWNER_READ, 'r');
            set(out, 2, s, PosixFilePermission.OWNER_WRITE, 'w');
            set(out, 3, s, PosixFilePermission.OWNER_EXECUTE, 'x');
            set(out, 4, s, PosixFilePermission.GROUP_READ, 'r');
            set(out, 5, s, PosixFilePermission.GROUP_WRITE, 'w');
            set(out, 6, s, PosixFilePermission.GROUP_EXECUTE, 'x');
            set(out, 7, s, PosixFilePermission.OTHERS_READ, 'r');
            set(out, 8, s, PosixFilePermission.OTHERS_WRITE, 'w');
            set(out, 9, s, PosixFilePermission.OTHERS_EXECUTE, 'x');
        } catch (IOException | UnsupportedOperationException e) {
            boolean r = Files.isReadable(p);
            boolean w = Files.isWritable(p);
            boolean x = Files.isExecutable(p);
            out[1] = out[4] = out[7] = r ? 'r' : '-';
            out[2] = out[5] = out[8] = w ? 'w' : '-';
            out[3] = out[6] = out[9] = x ? 'x' : '-';
        }
        return new String(out);
    }

    private static void set(char[] out, int idx, Set<PosixFilePermission> s, PosixFilePermission perm, char c) {
        out[idx] = s.contains(perm) ? c : '-';
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static CreeperError io(IOException e) {
        return new CreeperError(Protocol.ERR_IO, e.getMessage() == null ? "I/O error" : e.getMessage());
    }
}
