

package dev.demonzdevelopment.creepercli.api;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ActionRegistry {
    private final Map<String, ActionHandler> handlers = new ConcurrentHashMap<>();

    public void registerAction(String action, ActionHandler handler) {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("Action name cannot be null or blank");
        }
        if (handler == null) {
            throw new IllegalArgumentException("ActionHandler cannot be null");
        }
        handlers.put(action, handler);
    }

    public boolean unregisterAction(String action) {
        if (action == null) return false;
        return handlers.remove(action) != null;
    }

    public ActionHandler getHandler(String action) {
        if (action == null) return null;
        return handlers.get(action);
    }

    public boolean hasAction(String action) {
        return action != null && handlers.containsKey(action);
    }

    public Set<String> registeredActions() {
        return Collections.unmodifiableSet(handlers.keySet());
    }

    public void clear() {
        handlers.clear();
    }
}
