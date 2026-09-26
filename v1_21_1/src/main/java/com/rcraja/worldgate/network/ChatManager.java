package com.rcraja.worldgate.network;

import com.google.gson.JsonObject;

import java.util.function.BiConsumer;

public class ChatManager {

    private final RoomChannel channel;

    public ChatManager(
            FirebaseSession session
    ) {
        this.channel =
                new RoomChannel(
                        session,
                        "chat"
                );
    }

    public void sendMessage(
            String roomCode,
            String text
    ) {

        if (text == null) {
            return;
        }

        String clean =
                text.trim();

        if (clean.isEmpty()) {
            return;
        }

        if (clean.length() > 200) {
            clean =
                    clean.substring(
                            0,
                            200
                    );
        }

        JsonObject payload =
                new JsonObject();

        payload.addProperty(
                "text",
                clean
        );

        channel.send(
                roomCode,
                payload
        );
    }

    public void listen(
            String roomCode,
            BiConsumer<String, String> onMessage
    ) {

        channel.listen(
                roomCode,
                (uid, obj) -> {

                    String text = "";

                    if (obj.has("text")
                            && !obj.get("text").isJsonNull()) {

                        text =
                                obj.get("text")
                                        .getAsString();
                    }

                    if (!text.isBlank()) {

                        onMessage.accept(
                                uid,
                                text
                        );
                    }
                }
        );
    }

    public void stopListening() {
        channel.stopListening();
    }
}
