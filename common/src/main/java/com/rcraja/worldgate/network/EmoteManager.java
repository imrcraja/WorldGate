package com.rcraja.worldgate.network;

import com.google.gson.JsonObject;

import java.util.function.BiConsumer;

/**
 * Broadcasts a named emote to everyone in the room via /rooms/<code>/emotes.
 * Placeholder emote list for now -- RC RAJA is adding the real emote set
 * and animations later. This just wires up send/receive so that work
 * drops in without touching the networking layer again.
 */
public class EmoteManager {
    public static final String[] DEFAULT_EMOTES = { "wave", "dance", "sit", "cheer" };

    private final RoomChannel channel;

    public EmoteManager(FirebaseSession session) {
        this.channel = new RoomChannel(session, "emotes");
    }

    public void sendEmote(String roomCode, String emoteName) {
        JsonObject payload = new JsonObject();
        payload.addProperty("emote", emoteName);
        channel.send(roomCode, payload);
        try { EmoteAnimationState.play(java.util.UUID.fromString(channelUidFallback()), emoteName); } catch (Exception ignored) {}
    }

    /** onEmote receives (senderUid, emoteName) for every new/existing broadcast. */
    public void listen(String roomCode, BiConsumer<String, String> onEmote) {
        channel.listen(roomCode, (uid, obj) ->
                String emote = obj.has("emote") ? obj.get("emote").getAsString() : "?";
                try { EmoteAnimationState.play(java.util.UUID.fromString(uid), emote); } catch (Exception ignored) {}
                onEmote.accept(uid, emote));
    }

    private String channelUidFallback() { return "00000000-0000-0000-0000-000000000000"; }

    public void stopListening() {
        channel.stopListening();
    }
}
