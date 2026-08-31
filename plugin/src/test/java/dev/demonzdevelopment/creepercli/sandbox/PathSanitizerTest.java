

package dev.demonzdevelopment.creepercli.sandbox;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathSanitizerTest {

    @TempDir
    Path tempRoot;

    private PathSanitizer sanitizer;

    @BeforeEach
    void setUp() throws IOException {
        sanitizer = new PathSanitizer(tempRoot);
    }

    @Test
    void resolvesPlainPathsInsideJail() throws Exception {
        Path resolved = sanitizer.resolve("", "plugins/x.yml");
        assertEquals(tempRoot.resolve("plugins/x.yml"), resolved);
        assertTrue(resolved.toString().startsWith(tempRoot.toRealPath().toString()));
    }

    @Test
    void resolvesDotAsRoot() throws Exception {
        assertEquals(tempRoot.toRealPath(), sanitizer.resolve("", "."));
        assertEquals(tempRoot.toRealPath(), sanitizer.resolve("", "/"));
        assertEquals(tempRoot.toRealPath(), sanitizer.resolve("plugins", ".."));
    }

    @Test
    void resolvesCwdRelativePaths() throws Exception {
        Files.createDirectories(tempRoot.resolve("a/b"));
        Path resolved = sanitizer.resolve("a", "b/c.txt");
        assertEquals(tempRoot.resolve("a/b/c.txt"), resolved);
        assertEquals(tempRoot.resolve("a/b").toRealPath(), resolved.getParent());
    }

    @Test
    void normalizesDotDotSegmentsInsideJail() throws Exception {
        Files.createDirectories(tempRoot.resolve("a/b"));
        Path resolved = sanitizer.resolve("a", "b/../c.txt");
        assertEquals(tempRoot.resolve("a/c.txt"), resolved);
    }

    @Test
    void rejectsParentTraversal() {
        assertThrows(PathEscapeException.class, () -> sanitizer.resolve("", "../secret"));
        assertThrows(PathEscapeException.class, () -> sanitizer.resolve("", "a/../../../../etc/passwd"));
        assertThrows(PathEscapeException.class, () -> sanitizer.resolve("plugins", "../../../../etc"));
    }

    @Test
    void rejectsDeepTraversalFromSubdir() throws Exception {
        assertThrows(PathEscapeException.class, () -> sanitizer.resolve("plugins/sub", "..\\..\\.."));
        assertTrue(sanitizer.isInside(sanitizer.resolve("plugins", "..")));
    }

    @Test
    void rejectsBackslashSeparators() {
        assertThrows(PathEscapeException.class, () -> sanitizer.resolve("", "..\\..\\etc"));
        assertThrows(PathEscapeException.class, () -> sanitizer.resolve("", "a\\..\\..\\etc"));
    }

    @Test
    void rejectsDriveLetterPaths() {
        assertThrows(PathEscapeException.class, () -> sanitizer.resolve("", "C:/windows/win.ini"));
        assertThrows(PathEscapeException.class, () -> sanitizer.resolve("", "C:\\windows"));
    }

    @Test
    void rejectsNulBytes() {
        assertThrows(PathEscapeException.class, () -> sanitizer.resolve("", "a\u0000b"));
    }

    @Test
    void treatsEncodedDotsAsLiteralFilenames() throws Exception {
        Path resolved = sanitizer.resolve("", "..%2F..%2Fetc");
        assertTrue(sanitizer.isInside(resolved));
        assertFalse(resolved.toAbsolutePath().toString().equals("/etc"));
        assertEquals("..%2f..%2fetc", tempRoot.relativize(resolved).toString().toLowerCase());
    }

    @Test
    void followsSymlinkInsideJail() throws Exception {
        Files.createDirectories(tempRoot.resolve("real"));
        Path realFile = tempRoot.resolve("real/target.txt");
        Files.writeString(realFile, "hi");
        Path link = tempRoot.resolve("link.txt");
        try {
            Files.createSymbolicLink(link, realFile);
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.assumeTrue(false, "symlinks not supported here");
        }
        Path resolved = sanitizer.resolve("", "link.txt");
        assertEquals(realFile.toRealPath(), resolved);
    }

    @Test
    void resolvesSymlinkChainInsideJail() throws Exception {
        Files.createDirectories(tempRoot.resolve("d"));
        Path target = tempRoot.resolve("d/real.txt");
        Files.writeString(target, "hi");
        try {
            Files.createSymbolicLink(tempRoot.resolve("a"), tempRoot.resolve("d"));
            Files.createSymbolicLink(tempRoot.resolve("b"), tempRoot.resolve("a"));
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.assumeTrue(false, "symlinks not supported here");
        }
        Path resolved = sanitizer.resolve("", "b/real.txt");
        assertEquals(target.toRealPath(), resolved);
    }

    @Test
    void blocksSymlinkEscape() throws Exception {
        Path outside = tempRoot.getParent().resolve("secret-" + System.nanoTime());
        Files.createDirectories(tempRoot.resolve("x"));
        Files.writeString(outside, "top secret");
        try {
            Files.createSymbolicLink(tempRoot.resolve("x/evil"), outside);
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.assumeTrue(false, "symlinks not supported here");
        }
        assertThrows(PathEscapeException.class, () -> sanitizer.resolve("", "x/evil/passwd"));
        assertThrows(PathEscapeException.class, () -> sanitizer.resolve("", "x/evil"));
        Files.deleteIfExists(outside);
    }

    @Test
    void resolvesNonExistentDeepPathViaNearestExistingParent() throws Exception {
        Files.createDirectories(tempRoot.resolve("a/b"));
        Path resolved = sanitizer.resolve("", "a/b/c/d.txt");
        assertEquals(tempRoot.resolve("a/b/c/d.txt"), resolved);
        assertTrue(sanitizer.isInside(resolved));
    }

    @Test
    void rejectsTraversalOnNonExistentDeepPath() {
        assertThrows(PathEscapeException.class, () -> sanitizer.resolve("", "a/../../../../etc/shadow"));
    }

    @Test
    void jailPathRoundsTrip() throws Exception {
        Path p = sanitizer.resolve("", "plugins/x.yml");
        assertEquals("/plugins/x.yml", sanitizer.toJailPath(p));
        assertEquals("/", sanitizer.toJailPath(sanitizer.root()));
    }

    @Test
    void rootIsProtectedFromSymlinkSwap() throws Exception {
        Path outside = tempRoot.getParent().resolve("swap-" + System.nanoTime());
        Files.createDirectories(tempRoot.resolve("d"));
        try {
            Files.createSymbolicLink(tempRoot.resolve("d/out"), outside);
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.assumeTrue(false, "symlinks not supported here");
        }
        assertThrows(PathEscapeException.class, () -> sanitizer.resolve("d", "out"));
        Files.deleteIfExists(outside);
    }

    @Test
    void emptyAndBlankPathsResolveToCwd() throws Exception {
        assertEquals(sanitizer.root(), sanitizer.resolve("", ""));
        assertEquals(sanitizer.root(), sanitizer.resolve("", "   ".trim()));
    }

    @Test
    void absolutePathsIgnoreCwd() throws Exception {
        Files.createDirectories(tempRoot.resolve("a/b"));
        assertEquals(tempRoot.resolve("a/b"), sanitizer.resolve("x/y", "/a/b"));
        assertEquals(tempRoot.toRealPath(), sanitizer.resolve("a/b", "/"));
    }
}
