package com.rcraja.worldgate.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LocalWorldGateData {
    private LocalWorldGateData() {}
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Object LOCK = new Object();
    private static JsonObject data = new JsonObject();

    public static void load() {
        synchronized (LOCK) {
            try {
                Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("config");
                Path file = dir.resolve("worldgate-player.json");
                Files.createDirectories(dir);
                if (Files.exists(file)) {
                    data = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
                    if (data == null) data = new JsonObject();
                }
            } catch (Exception ignored) { data = new JsonObject(); }
        }
    }

    public static String get(String key, String fallback) {
        synchronized (LOCK) { return data.has(key) ? data.get(key).getAsString() : fallback; }
    }

    public static String get(String key) {
        synchronized (LOCK) {
            return data.has(key) ? data.get(key).getAsString() : null;
        }
    }

    public static void set(String key, String value) {
        synchronized (LOCK) {
            data.addProperty(key, value);
            persist();
        }
    }
}
