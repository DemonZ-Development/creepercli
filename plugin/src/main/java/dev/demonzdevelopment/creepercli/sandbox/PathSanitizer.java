/*
 * Copyright 2026 DemonZDevelopment
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.demonzdevelopment.creepercli.sandbox;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.regex.Pattern;

public final class PathSanitizer {
    private static final Pattern DRIVE_LETTER = Pattern.compile("^[A-Za-z]:.*");

    private final Path root;
    private final String rootPrefix;

    public PathSanitizer(Path root) throws IOException {
        Files.createDirectories(root);
        this.root = root.toRealPath();
        this.rootPrefix = this.root.toString() + File.separatorChar;
    }

    public Path root() {
        return root;
    }

    public Path resolve(String cwd, String requested) throws PathEscapeException {
        if (requested == null || requested.isEmpty()) {
            requested = ".";
        }
        if (requested.equals("~") || requested.startsWith("~/")) {
            requested = "/" + (requested.length() > 2 ? requested.substring(2) : "");
        }
        if (requested.indexOf('\0') >= 0) {
            throw new PathEscapeException("NUL byte in path");
        }
        if (requested.contains("\\")) {
            throw new PathEscapeException("Backslash separators are not allowed");
        }
        if (DRIVE_LETTER.matcher(requested).matches()) {
            throw new PathEscapeException("Drive-absolute paths are not allowed");
        }

        boolean absolute = requested.startsWith("/");
        String rel = requested;
        while (rel.startsWith("/")) {
            rel = rel.substring(1);
        }

        String base = (absolute || cwd == null || cwd.isEmpty()) ? "" : cwd;
        Path joined = root.resolve(base).resolve(rel).normalize();
        if (!isInside(joined)) {
            throw new PathEscapeException("Path escapes jail: " + requested);
        }
        try {
            return resolveReal(joined);
        } catch (IOException e) {
            throw new PathEscapeException("Failed to resolve path: " + e.getMessage());
        }
    }

    private Path resolveReal(Path p) throws IOException, PathEscapeException {
        Path existing = nearestExisting(p);
        Path real = existing.toRealPath();
        if (!isInside(real)) {
            throw new PathEscapeException("Symlink escape detected");
        }
        Path suffix = existing.relativize(p);
        Path candidate = real.resolve(suffix).normalize();
        if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            Path realCandidate = candidate.toRealPath();
            if (!isInside(realCandidate)) {
                throw new PathEscapeException("Symlink escape detected");
            }
            return realCandidate;
        }
        return candidate;
    }

    private Path nearestExisting(Path p) {
        Path cur = p;
        while (!Files.exists(cur, LinkOption.NOFOLLOW_LINKS)) {
            Path parent = cur.getParent();
            if (parent == null) return root;
            cur = parent;
        }
        return cur;
    }

    public boolean isInside(Path p) {
        String s = p.normalize().toAbsolutePath().toString();
        return s.equals(root.toString()) || s.startsWith(rootPrefix);
    }

    public String toJailPath(Path p) {
        Path rel = root.relativize(p.normalize());
        String s = rel.toString().replace(File.separatorChar, '/');
        return s.isEmpty() ? "/" : "/" + s;
    }
}
