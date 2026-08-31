

package dev.demonzdevelopment.creepercli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public final class Json {
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private Json() {
    }

    public static JsonObject parseObject(String s) throws CreeperError {
        try {
            JsonElement el = JsonParser.parseString(s);
            if (el == null || !el.isJsonObject()) {
                throw new CreeperError(Protocol.ERR_BAD_REQUEST, "Expected a JSON object");
            }
            return el.getAsJsonObject();
        } catch (JsonSyntaxException e) {
            throw new CreeperError(Protocol.ERR_BAD_REQUEST, "Malformed JSON: " + e.getMessage());
        }
    }

    public static String opt(JsonObject o, String key, String def) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return def;
        JsonElement el = o.get(key);
        if (!el.isJsonPrimitive()) return def;
        return el.getAsString();
    }

    public static int optInt(JsonObject o, String key, int def) {
        if (o == null || !o.has(key)) return def;
        try {
            return o.get(key).getAsInt();
        } catch (Exception e) {
            return def;
        }
    }

    public static long optLong(JsonObject o, String key, long def) {
        if (o == null || !o.has(key)) return def;
        try {
            return o.get(key).getAsLong();
        } catch (Exception e) {
            return def;
        }
    }

    public static boolean optBool(JsonObject o, String key, boolean def) {
        if (o == null || !o.has(key)) return def;
        try {
            return o.get(key).getAsBoolean();
        } catch (Exception e) {
            return def;
        }
    }

    public static JsonObject ok() {
        return new JsonObject();
    }
}
