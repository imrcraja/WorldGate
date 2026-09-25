package com.rcraja.worldgate.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rcraja.worldgate.client.LocalWorldGateData;
import com.rcraja.worldgate.client.WorldGateModClient;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles WorldGate's existing Discord OAuth link flow.
 * Voice transport itself is intentionally not faked here; the official Discord
 * Social SDK is a native C++ integration and needs its native runtime.
 */
public final class DiscordLinkManager {
    private static final String TICKET_KEY = "discordLinkTicket";
    private static final String ID_KEY = "discordId";
    private static final String NAME_KEY = "discordName";
    private static final AtomicBoolean LINKING = new AtomicBoolean(false);

    private DiscordLinkManager() {}

    public static boolean isLinked() {
        return LocalWorldGateData.get(ID_KEY) != null
                && !LocalWorldGateData.get(ID_KEY).isBlank();
    }

    public static String linkedName() {
        String name = LocalWorldGateData.get(NAME_KEY);
        return name == null || name.isBlank() ? "Linked Discord" : name;
    }

    public static void begin(Runnable callback) {
        if (!WorldGateModClient.SESSION.isReady() || !LINKING.compareAndSet(false, true)) {
            if (callback != null) callback.run();
            return;
        }

        WorldGateModClient.EXECUTOR.submit(() -> {
            try {
                String response = BackendClient.discordAuthorize(WorldGateModClient.SESSION);
                if (response == null) return;

                JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                String url = json.has("authorizeUrl") ? json.get("authorizeUrl").getAsString() : "";
                String ticket = json.has("ticket") ? json.get("ticket").getAsString() : "";
                if (url.isBlank() || ticket.isBlank()) return;

                LocalWorldGateData.set(TICKET_KEY, ticket);
                net.minecraft.util.Util.getPlatform().openUri(java.net.URI.create(url));

                for (int i = 0; i < 120; i++) {
                    Thread.sleep(2000L);
                    String result = BackendClient.discordResult(ticket);
                    if (result == null) continue;
                    JsonObject r = JsonParser.parseString(result).getAsJsonObject();
                    if (!"COMPLETED".equalsIgnoreCase(r.has("status") ? r.get("status").getAsString() : "")) continue;
                    JsonObject linked = r.has("linked") && r.get("linked").isJsonObject()
                            ? r.getAsJsonObject("linked") : null;
                    if (linked != null) {
                        if (linked.has("discordId")) LocalWorldGateData.set(ID_KEY, linked.get("discordId").getAsString());
                        if (linked.has("username")) LocalWorldGateData.set(NAME_KEY, linked.get("username").getAsString());
                    }
                    LocalWorldGateData.remove(TICKET_KEY);
                    break;
                }
            } catch (Exception ignored) {
            } finally {
                LINKING.set(false);
                if (callback != null && minecraftAvailable()) {
                    net.minecraft.client.Minecraft.getInstance().execute(callback);
                }
            }
        });
    }

    private static boolean minecraftAvailable() {
        return net.minecraft.client.Minecraft.getInstance() != null;
    }
}
