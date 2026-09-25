package com.rcraja.worldgate.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rcraja.worldgate.WorldGateMod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WorldGate skin cache. Skin URLs are discovered from the player's
 * WorldGate profile and kept in memory so rendering never blocks on Firebase.
 */
public final class WorldGateSkinCache {
    private static final Map<UUID, String> SKIN_URLS = new ConcurrentHashMap<>();

    private WorldGateSkinCache() {}

    public static void put(UUID uid, String url) {
        if (uid == null || url == null || url.isBlank()) return;
        SKIN_URLS.put(uid, url.trim());
    }

    public static String get(UUID uid) {
        return uid == null ? null : SKIN_URLS.get(uid);
    }

    public static void refreshRoomPlayers(String roomJson) {
        if (roomJson == null || roomJson.isBlank() || "null".equals(roomJson)) return;
        try {
            JsonObject room = JsonParser.parseString(roomJson).getAsJsonObject();
            if (!room.has("players") || !room.get("players").isJsonObject()) return;
            for (String uidText : room.getAsJsonObject("players").keySet()) {
                try {
                    UUID uid = UUID.fromString(uidText);
                    WorldGateModClient.EXECUTOR.submit(() -> {
                        String url = WorldGateModClient.FRIEND_MANAGER.getSkinUrl(uidText);
                        if (url != null && !url.isBlank()) put(uid, url);
                    });
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (Exception e) {
            WorldGateMod.LOGGER.debug("WorldGate skin cache refresh failed", e);
        }
    }
}
