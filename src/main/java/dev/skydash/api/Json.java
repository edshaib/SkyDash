package dev.skydash.api;
import com.google.gson.*;
public final class Json {
    private Json() {
    }
    public static JsonObject obj(JsonObject o,String key) {
        return o!=null&&o.has(key)&&o.get(key).isJsonObject()?o.getAsJsonObject(key):new JsonObject();
    }
    public static JsonArray arr(JsonObject o,String key) {
        return o!=null&&o.has(key)&&o.get(key).isJsonArray()?o.getAsJsonArray(key):new JsonArray();
    }
    public static String str(JsonObject o,String key,String fallback) {
        try {
            return o.get(key).getAsString();
        } catch(RuntimeException e) {
            return fallback;
        }
    }
    public static double num(JsonObject o,String key) {
        try {
            return o.get(key).getAsDouble();
        } catch(RuntimeException e) {
            return 0;
        }
    }
    public static boolean bool(JsonObject o,String key) {
        try {
            return o.get(key).getAsBoolean();
        } catch(RuntimeException e) {
            return false;
        }
    }
}
