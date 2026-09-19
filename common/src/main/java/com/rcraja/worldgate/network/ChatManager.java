package com.rcraja.worldgate.network;

import com.google.gson.JsonObject;

import java.util.function.BiConsumer;

/**
 * Sends/receives chat messages for a room under /rooms/<code>/chat.
 * Plain text only for now -- clickable links and delete/reply need
 * version-specific Style/ClickEvent handling, which lives in each
 * v*_*_* module's chat-hookup code once that's built, not here.
 */
public class ChatManager {
    private final RoomChannel channel;

    public ChatManager(FirebaseSession session) {
        this.channel = new RoomChannel(session, "chat");
    }

    public void sendMessage(String roomCode, String text) {
        JsonObject payload = new JsonObject();
        payload.addProperty("text", text);
        channel.send(roomCode, payload);
    }

    /** onMessage receives (senderUid, text) for every new/existing chat entry. */
    public void listen(String roomCode, BiConsumer<String, String> onMessage) {
        channel.listen(roomCode, (uid, obj) ->
                onMessage.accept(uid, obj.has("text") ? obj.get("text").getAsString() : ""));
    }

    public void stopListening() {
        channel.stopListening();
    }
}
