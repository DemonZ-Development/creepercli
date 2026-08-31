

package dev.demonzdevelopment.creepercli;

import java.util.List;
import java.util.Locale;

public final class ExecAllowlist {
    public record Check(boolean allowed, String matched) {
        public static Check deny() {
            return new Check(false, null);
        }
    }

    private final List<String> patterns;

    public ExecAllowlist(PluginConfig cfg) {
        this.patterns = cfg.execAllowlist();
    }

    public Check check(String command) {
        if (command == null) return Check.deny();
        String c = command.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        String best = null;
        int bestLen = -1;
        for (String raw : patterns) {
            String p = raw.trim().toLowerCase(Locale.ROOT);
            if (p.isEmpty()) continue;
            if (p.endsWith("*")) {
                String prefix = p.substring(0, p.length() - 1).trim();
                
                
                
                
                boolean matched;
                if (prefix.isEmpty()) {
                    matched = true;
                } else if (c.equals(prefix)) {
                    matched = true;
                } else {
                    matched = c.startsWith(prefix) && c.length() > prefix.length() && Character.isWhitespace(c.charAt(prefix.length()));
                }
                if (matched && prefix.length() > bestLen) {
                    bestLen = prefix.length();
                    best = raw.trim();
                }
            } else if (c.equals(p) && p.length() > bestLen) {
                bestLen = p.length();
                best = raw.trim();
            }
        }
        return best != null ? new Check(true, best) : Check.deny();
    }
}
