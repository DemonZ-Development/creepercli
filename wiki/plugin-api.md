# Third-party plugin API

CreeperCLI exposes an extension API so other Paper/Spigot plugins can register custom RPC actions on its TCP server.

With it you can:

- add remote CLI commands that talk to your plugin;
- expose admin hooks, minigame controls, or automation endpoints to CLI sessions;
- inherit CreeperCLI's authentication, rate limiting, and audit logging for free.

All extension classes live under `dev.demonzdevelopment.creepercli.api`.

## `ActionHandler`

```java
package dev.demonzdevelopment.creepercli.api;

import com.google.gson.JsonObject;
import dev.demonzdevelopment.creepercli.CreeperError;
import dev.demonzdevelopment.creepercli.net.ClientConnection;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface ActionHandler {
    CompletableFuture<JsonObject> handle(ClientConnection connection, JsonObject params) throws CreeperError;
}
```

## `ActionRegistry`

```java
CreeperCLIPlugin creeper = CreeperCLIPlugin.getPlugin(CreeperCLIPlugin.class);
ActionRegistry registry = creeper.actionRegistry();
```

Methods:

- `registerAction(String action, ActionHandler handler)` registers a custom action;
- `unregisterAction(String action)` removes one;
- `hasAction(String action)` checks registration;
- `registeredActions()` returns an unmodifiable set of action names;
- `clear()` removes all handlers.

## Example

A plugin that registers a `myplugin.announce` action:

```java
package com.example.myplugin;

import com.google.gson.JsonObject;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.CreeperError;
import dev.demonzdevelopment.creepercli.Json;
import dev.demonzdevelopment.creepercli.Protocol;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

public final class MyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        CreeperCLIPlugin creeper = JavaPlugin.getPlugin(CreeperCLIPlugin.class);

        creeper.actionRegistry().registerAction("myplugin.announce", (conn, params) -> {
            String message = Json.opt(params, "message", null);
            if (message == null || message.isBlank()) {
                throw new CreeperError(Protocol.ERR_INVALID_PARAMS, "message parameter is required");
            }

            CompletableFuture<JsonObject> future = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(this, () -> {
                Bukkit.broadcastMessage("\u00a7a[Announcement] \u00a7f" + message);
                JsonObject res = Json.ok();
                res.addProperty("announced", true);
                res.addProperty("recipientCount", Bukkit.getOnlinePlayers().size());
                future.complete(res);
            });

            return future;
        });
    }

    @Override
    public void onDisable() {
        CreeperCLIPlugin creeper = JavaPlugin.getPlugin(CreeperCLIPlugin.class);
        if (creeper != null) {
            creeper.actionRegistry().unregisterAction("myplugin.announce");
        }
    }
}
```

## Guidelines

1. **Authentication is automatic.** Only authenticated CLI sessions can trigger registered actions.
2. **Stay off the main thread.** TCP RPC calls run asynchronously. When touching the Bukkit API (broadcasting, teleporting, mutating worlds), delegate with `Bukkit.getScheduler().runTask()` and complete the future from there.
3. **Throw `CreeperError`** with a standard code (`Protocol.ERR_INVALID_PARAMS`, `Protocol.ERR_FORBIDDEN`, `Protocol.ERR_INTERNAL`) to return a clean error frame to the CLI.
4. **Validate input.** Params come over the wire; treat them as untrusted.
