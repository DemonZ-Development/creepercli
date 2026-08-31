

package dev.demonzdevelopment.creepercli.monitor;

import com.google.gson.JsonObject;
import dev.demonzdevelopment.creepercli.CreeperCLIPlugin;
import dev.demonzdevelopment.creepercli.CreeperError;
import dev.demonzdevelopment.creepercli.Protocol;
import dev.demonzdevelopment.creepercli.net.ClientConnection;
import org.bukkit.Bukkit;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
    private long lineCounter = 0;

    public LogStreamer(CreeperCLIPlugin plugin) {
        this.plugin = plugin;
    }

        public void feed(String level, String message, long millis) {
        LogRecord record = new LogRecord(toJulLevel(level), message == null ? "" : message);
        record.setMillis(millis);
        onPublish(record);
    }

    private static java.util.logging.Level toJulLevel(String name) {
        if (name != null) {
            switch (name.toUpperCase(java.util.Locale.ROOT)) {
                case "ERROR", "FATAL": return java.util.logging.Level.SEVERE;
                case "WARN": return java.util.logging.Level.WARNING;
                case "DEBUG": return java.util.logging.Level.FINE;
                case "TRACE": return java.util.logging.Level.FINEST;
            }
        }
        return java.util.logging.Level.INFO;
    }

    public void attach() {
        plugin.platform().logger().addHandler(handler);
    }

    public void detach() {
        plugin.platform().logger().removeHandler(handler);
    }

    private void onPublish(LogRecord record) {
        String message = record.getMessage() == null ? "" : record.getMessage();
        String stamp = timeFormat.get().format(new Date(record.getMillis()));
        String display = "[" + stamp + "] [" + record.getLevel().getName() + "] " + message;
        synchronized (ring) {
            ring.addLast(display);
            lineCounter++;
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
            if (filter == NULL_PATTERN || filter.matcher(display).find()) {
                entry.getKey().sendEvent(Protocol.EVENT_LOG_LINE, data);
            }
        }
    }

    private static final Pattern NULL_PATTERN = Pattern.compile("(?!)");

    public void subscribe(ClientConnection conn, String grep) throws CreeperError {
        Pattern pattern = NULL_PATTERN;
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

    public void unsubscribeAll() {
        subscribers.clear();
    }

    public List<String> buffer() {
        synchronized (ring) {
            return List.copyOf(ring);
        }
    }

    public long mark() {
        synchronized (ring) {
            return lineCounter;
        }
    }

    public List<String> since(long mark) {
        synchronized (ring) {
            int wanted = (int) Math.min(ring.size(), lineCounter - mark);
            if (wanted <= 0) return List.of();
            Object[] arr = ring.toArray();
            int from = arr.length - wanted;
            List<String> out = new ArrayList<>(wanted);
            for (int i = from; i < arr.length; i++) out.add((String) arr[i]);
            return out;
        }
    }

    public int subscriberCount() {
        return subscribers.size();
    }
}
