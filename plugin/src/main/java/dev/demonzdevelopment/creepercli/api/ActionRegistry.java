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
