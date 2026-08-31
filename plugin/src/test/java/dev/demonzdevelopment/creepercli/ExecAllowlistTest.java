

package dev.demonzdevelopment.creepercli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecAllowlistTest {

    private ExecAllowlist allowlist(List<String> patterns) {
        PluginConfig cfg = new PluginConfig(patterns);
        return new ExecAllowlist(cfg);
    }

    @Test
    void exactMatchReturnsMatchedPattern() {
        ExecAllowlist al = allowlist(List.of("list", "restart"));
        ExecAllowlist.Check c = al.check("list");
        assertTrue(c.allowed());
        assertEquals("list", c.matched());
    }

    @Test
    void prefixWildcardMatchesAnythingStartingWithPrefix() {
        ExecAllowlist al = allowlist(List.of("say *", "whitelist *"));
        assertTrue(al.check("say hello world").allowed());
        assertEquals("say *", al.check("say hello world").matched());
        assertTrue(al.check("whitelist add steve").allowed());
    }

    @Test
    void caseAndWhitespaceAreNormalized() {
        ExecAllowlist al = allowlist(List.of("list", "say *"));
        assertTrue(al.check("  LIST  ").allowed());
        assertTrue(al.check("SAY  hi").allowed());
    }

    @Test
    void prefixMustMatchFromStartOfTrimmedCommand() {
        ExecAllowlist al = allowlist(List.of("say *"));
        assertFalse(al.check("saying stuff").allowed());
        assertTrue(al.check("say hi").allowed());
    }

    @Test
    void unknownCommandIsDenied() {
        ExecAllowlist al = allowlist(List.of("list", "say *"));
        ExecAllowlist.Check c = al.check("op steve");
        assertFalse(c.allowed());
        assertNull(c.matched());
    }

    @Test
    void emptyOrBlankPatternsAreSkipped() {
        ExecAllowlist al = allowlist(java.util.Arrays.asList("", "  ", "list"));
        assertTrue(al.check("list").allowed());
        assertFalse(al.check("anything-else").allowed());
    }

    @Test
    void longestMatchWinsForDiagnostic() {
        
        
        ExecAllowlist al = allowlist(List.of("say *", "say hello"));
        ExecAllowlist.Check c = al.check("say hello");
        assertTrue(c.allowed());
        assertEquals("say hello", c.matched());
    }

    @Test
    void nullCommandIsDenied() {
        ExecAllowlist al = allowlist(List.of("list"));
        assertFalse(al.check(null).allowed());
    }
}