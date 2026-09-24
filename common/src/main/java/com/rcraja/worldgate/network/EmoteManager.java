package com.rcraja.worldgate.network;

import com.google.gson.JsonObject;

import java.util.function.BiConsumer;
import java.util.Set;
import java.util.Locale;

/**
 * Broadcasts validated named emotes to everyone in the room and updates the
 * client animation state for local and remote players.
 */
public class EmoteManager {
    public static final String[] DEFAULT_EMOTES = { "wave", "dance", "sit", "cheer" };
    private static final Set<String> ALLOWED_EMOTES = Set.of(DEFAULT_EMOTES);

    private final RoomChannel channel;
    private final FirebaseSession session;

    public EmoteManager(FirebaseSession session) {
        this.session = session;
        this.channel = new RoomChannel(session, "emotes");
    }

    public void sendEmote(String roomCode, String emoteName) {
        String clean = emoteName == null ? "" : emoteName.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_EMOTES.contains(clean)) return;
        JsonObject payload = new JsonObject();
        payload.addProperty("emote", clean);
        channel.send(roomCode, payload);
        try { EmoteAnimationState.play(java.util.UUID.fromString(session.uid()), clean); } catch (Exception ignored) {}
    }

    /** onEmote receives (senderUid, emoteName) for every new/existing broadcast. */
    public void listen(String roomCode, BiConsumer<String, String> onEmote) {
        channel.listen(roomCode, (uid, obj) -> {
            String emote = obj.has("emote") ? obj.get("emote").getAsString() : "";
            String clean = emote.trim().toLowerCase(Locale.ROOT);
            if (!ALLOWED_EMOTES.contains(clean)) return;
            try { EmoteAnimationState.play(java.util.UUID.fromString(uid), clean); } catch (Exception ignored) {}
            onEmote.accept(uid, clean);
        });
    }

    public void stopListening() {
        channel.stopListening();
    }
}
