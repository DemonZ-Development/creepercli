package dev.demonzdevelopment.creepercli.monitor;

import com.google.gson.JsonObject;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.CreeperError;
import dev.demonzdevelopment.creepercli.Protocol;
import dev.demonzdevelopment.creepercli.net.ClientConnection;
import org.bukkit.Bukkit;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class LogStreamer {
    private final CreeperCLIPlugin plugin;
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            onPublish(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
        }
    };
    private final ArrayDeque<String> ring = new ArrayDeque<>();
    private final Map<ClientConnection, Pattern> subscribers = new ConcurrentHashMap<>();
    private final ThreadLocal<SimpleDateFormat> timeFormat = ThreadLocal.withInitial(() -> new SimpleDateFormat("HH:mm:ss"));

    public LogStreamer(CreeperCLIPlugin plugin) {
        this.plugin = plugin;
    }

    public void attach() {
        Bukkit.getLogger().addHandler(handler);
    }

    public void detach() {
        Bukkit.getLogger().removeHandler(handler);
    }

    private void onPublish(LogRecord record) {
        String message = record.getMessage() == null ? "" : record.getMessage();
        String stamp = timeFormat.get().format(new Date(record.getMillis()));
        String display = "[" + stamp + "] [" + record.getLevel().getName() + "] " + message;
        synchronized (ring) {
            ring.addLast(display);
            while (ring.size() > plugin.cfg().logBufferLines()) {
                ring.removeFirst();
            }
        }
        if (subscribers.isEmpty()) return;
        JsonObject data = new JsonObject();
        data.addProperty("level", record.getLevel().getName());
        data.addProperty("message", message);
        data.addProperty("ts", record.getMillis());
        for (Map.Entry<ClientConnection, Pattern> entry : subscribers.entrySet()) {
            Pattern filter = entry.getValue();
            if (filter == null || filter.matcher(display).find()) {
                entry.getKey().sendEvent(Protocol.EVENT_LOG_LINE, data);
            }
        }
    }

    public void subscribe(ClientConnection conn, String grep) throws CreeperError {
        Pattern pattern = null;
        if (grep != null && !grep.isEmpty()) {
            try {
                pattern = Pattern.compile(grep, Pattern.CASE_INSENSITIVE);
            } catch (PatternSyntaxException e) {
                throw new CreeperError(Protocol.ERR_INVALID_PARAMS, "Invalid grep pattern: " + e.getMessage());
            }
        }
        subscribers.put(conn, pattern);
    }

    public void unsubscribe(ClientConnection conn) {
        subscribers.remove(conn);
    }

    public List<String> buffer() {
        synchronized (ring) {
            return List.copyOf(ring);
        }
    }

    public int subscriberCount() {
        return subscribers.size();
    }
}
