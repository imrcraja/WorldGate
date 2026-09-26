package com.rcraja.worldgate.network;

import com.google.gson.JsonObject;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Room chat transport. The legacy text-only listener is kept for compatibility;
 * detailed listeners expose stable message ids, reply targets and deletions.
 */
public class ChatManager {

    private final RoomChannel channel;
    private final FirebaseSession session;
    private final FirebaseStreamClient typingStream = new FirebaseStreamClient();
    private volatile Consumer<String> typingChanged;

    public ChatManager(FirebaseSession session) {
        this.session = session;
        this.channel = new RoomChannel(session, "chat");
    }

    public void setTypingChangedListener(Consumer<String> listener) { typingChanged = listener; }

    public void startTypingRealtime(String roomCode) {
        if (!session.isReady() || roomCode == null || roomCode.isBlank()) return;
        typingStream.stop();
        typingStream.listen(com.rcraja.worldgate.Constants.FIREBASE_DATABASE_URL,
                "/chat_typing/" + roomCode, session.idToken(), data -> {
                    Consumer<String> listener = typingChanged;
                    if (listener != null) listener.accept(data);
                });
    }

    public void setTyping(String roomCode, boolean typing) {
        if (!session.isReady() || roomCode == null || roomCode.isBlank()) return;
        String uid = session.uid();
        if (uid == null || uid.isBlank()) return;
        String json = "{\"typing\":" + typing + ",\"lastSeen\":" + System.currentTimeMillis() + "}";
        session.db().put("/chat_typing/" + roomCode + "/" + uid, json);
    }

    public void sendMessage(String roomCode, String text) {
        sendMessage(roomCode, text, null);
    }

    public String sendMessage(String roomCode, String text, String replyTo) {
        if (text == null) return null;

        String clean = text.trim();
        if (clean.isEmpty()) return null;
        if (clean.length() > 200) clean = clean.substring(0, 200);

        String messageId = UUID.randomUUID().toString();

        JsonObject payload = new JsonObject();
        payload.addProperty("id", messageId);
        payload.addProperty("text", clean);
        if (replyTo != null && !replyTo.isBlank()) {
            payload.addProperty("replyTo", replyTo.trim());
        }

        channel.send(roomCode, payload);
        return messageId;
    }

    public void sendReply(String roomCode, String text, String replyTo) {
        sendMessage(roomCode, text, replyTo);
    }

    /**
     * Marks a message deleted for room listeners. The original message is not
     * physically removed, preserving moderation/audit history in the channel.
     */
    public void deleteMessage(String roomCode, String messageId) {
        if (messageId == null || messageId.isBlank()) return;

        JsonObject payload = new JsonObject();
        payload.addProperty("id", messageId.trim());
        payload.addProperty("deleted", true);
        channel.send(roomCode, payload);
    }

    public void listen(String roomCode, BiConsumer<String, String> onMessage) {
        channel.listen(roomCode, (uid, obj) -> {
            String text = obj.has("text") && !obj.get("text").isJsonNull()
                    ? obj.get("text").getAsString()
                    : "";

            if (!text.isBlank()) {
                onMessage.accept(uid, text);
            }
        });
    }

    /**
     * Detailed event listener for future chat UI actions.
     * event is one of MESSAGE or DELETE.
     */
    public void listenDetailed(String roomCode, Consumer<ChatEvent> onEvent) {
        currentRoom = roomCode;
        channel.listen(roomCode, (uid, obj) -> {
            String id = obj.has("id") && !obj.get("id").isJsonNull()
                    ? obj.get("id").getAsString()
                    : "";
            String text = obj.has("text") && !obj.get("text").isJsonNull()
                    ? obj.get("text").getAsString()
                    : "";
            String replyTo = obj.has("replyTo") && !obj.get("replyTo").isJsonNull()
                    ? obj.get("replyTo").getAsString()
                    : "";
            boolean deleted = obj.has("deleted") && !obj.get("deleted").isJsonNull()
                    && obj.get("deleted").getAsBoolean();

            if (deleted) {
                onEvent.accept(new ChatEvent(uid, id, "", replyTo, true));
                return;
            }

            if (!text.isBlank()) {
                onEvent.accept(new ChatEvent(uid, id, text, replyTo, false));
            }
        });
    }

    public void stopListening() {
        channel.stopListening();
        typingStream.stop();
        setTypingSafe(false);
    }

    private void setTypingSafe(boolean typing) {
        try { setTyping(currentRoom, typing); } catch (Exception ignored) {}
    }

    private volatile String currentRoom;

    public void bindTypingRoom(String roomCode) { currentRoom = roomCode; startTypingRealtime(roomCode); }

    public record ChatEvent(
            String senderUid,
            String messageId,
            String text,
            String replyTo,
            boolean deleted
    ) {}
}
