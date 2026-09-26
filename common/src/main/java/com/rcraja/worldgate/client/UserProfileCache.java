package com.rcraja.worldgate.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Small on-device mirror of the user's shareable WorldGate profile.
 *
 * Firebase remains authoritative. This cache is only the last known good
 * snapshot so profile/UID/friend-code information can be shown immediately
 * while offline or while Firebase reconnects.
 */
public final class UserProfileCache {
    private static final String KEY = "userProfileCache";
    private static final Object LOCK = new Object();

    private UserProfileCache() {}

    public static void save(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) return;
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            if (object.has("uid") && !object.get("uid").isJsonNull()) {
                LocalWorldGateData.set(KEY, object.toString());
            }
        } catch (Exception ignored) {
        }
    }

    public static String raw() {
        synchronized (LOCK) {
            return LocalWorldGateData.get(KEY);
        }
    }

    public static JsonObject read() {
        String raw = raw();
        if (raw == null || raw.isBlank() || "null".equals(raw)) return null;
        try {
            return JsonParser.parseString(raw).getAsJsonObject();
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String value(String key, String fallback) {
        JsonObject object = read();
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
        try {
            return object.get(key).getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static void clear() {
        LocalWorldGateData.remove(KEY);
    }
}
