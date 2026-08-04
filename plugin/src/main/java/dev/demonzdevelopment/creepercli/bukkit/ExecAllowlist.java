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

package dev.demonzdevelopment.creepercli.bukkit;

import dev.demonzdevelopment.creepercli.PluginConfig;

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
                if (c.startsWith(prefix) && prefix.length() > bestLen) {
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
