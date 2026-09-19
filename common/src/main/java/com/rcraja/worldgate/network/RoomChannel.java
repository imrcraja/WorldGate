package com.rcraja.worldgate.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rcraja.worldgate.WorldGateMod;
import com.rcraja.worldgate.Constants;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Generic "broadcast a JSON object to everyone in a room, get notified of
 * new ones in real time" channel over a Realtime Database list at
 * /rooms/<code>/<channelName>. ChatManager and EmoteManager both sit on
 * top of this so the streaming/parsing/dedupe logic only exists once.
 *
 * Uses Firebase's server-sent-events REST streaming endpoint, so updates
 * arrive live without polling. Relies on Gson, which Minecraft already
 * ships on the classpath -- if a module's build fails on the Gson import,
 * add `compileOnly 'com.google.code.gson:gson:2.10.1'` to that module's
 * build.gradle.
 */
public class RoomChannel {
    private final FirebaseSession session;
    private final String channelName;
    private final FirebaseStreamClient stream = new FirebaseStreamClient();
    private final Set<String> seenKeys = ConcurrentHashMap.newKeySet();

    public RoomChannel(FirebaseSession session, String channelName) {
        this.session = session;
        this.channelName = channelName;
    }

    public void send(String roomCode, JsonObject payload) {
        if (!session.isReady()) return;
        payload.addProperty("uid", session.uid());
        payload.addProperty("sentAt", System.currentTimeMillis());
        session.db().post("/rooms/" + roomCode + "/" + channelName, payload.toString());
    }

    /** onEntry receives (senderUid, fullPayload) for every new/existing entry. */
    public void listen(String roomCode, BiConsumer<String, JsonObject> onEntry) {
        stream.listen(Constants.FIREBASE_DATABASE_URL, "/rooms/" + roomCode + "/" + channelName,
                session.idToken(), raw -> {
                    try {
                        JsonObject event = JsonParser.parseString(raw).getAsJsonObject();
                        String path = event.has("path") ? event.get("path").getAsString() : "/";
                        JsonElement dataEl = event.get("data");
                        if (dataEl == null || dataEl.isJsonNull()) return;

                        if (path.equals("/") && dataEl.isJsonObject()) {
                            JsonObject all = dataEl.getAsJsonObject();
                            for (String key : all.keySet()) {
                                tryEmit(key, all.get(key), onEntry);
                            }
                        } else if (dataEl.isJsonObject()) {
                            String key = path.startsWith("/") ? path.substring(1) : path;
                            tryEmit(key, dataEl, onEntry);
                        }
                    } catch (Exception e) {
                        WorldGateMod.LOGGER.error("WorldGate {} parse error", channelName, e);
                    }
                });
    }

    private void tryEmit(String key, JsonElement el, BiConsumer<String, JsonObject> onEntry) {
        if (key == null || !seenKeys.add(key)) return;
        if (!el.isJsonObject()) return;
        JsonObject obj = el.getAsJsonObject();
        String uid = obj.has("uid") ? obj.get("uid").getAsString() : "?";
        onEntry.accept(uid, obj);
    }

    public void stopListening() {
        stream.stop();
    }
}
