package com.rcraja.worldgate.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rcraja.worldgate.Constants;
import com.rcraja.worldgate.WorldGateMod;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class RoomChannel {

    private final FirebaseSession session;
    private final String channelName;

    private final FirebaseStreamClient stream =
            new FirebaseStreamClient();

    private final Set<String> seenKeys =
            ConcurrentHashMap.newKeySet();

    public RoomChannel(
            FirebaseSession session,
            String channelName
    ) {
        this.session = session;
        this.channelName = channelName;
    }

    public void send(
            String roomCode,
            JsonObject payload
    ) {
        if (!session.isReady()
                || roomCode == null
                || roomCode.isBlank()) {
            return;
        }

        payload.addProperty(
                "uid",
                session.uid()
        );

        payload.addProperty(
                "sentAt",
                System.currentTimeMillis()
        );

        session.db().post(
                "/rooms/"
                        + roomCode
                        + "/"
                        + channelName,
                payload.toString()
        );
    }

    public void listen(
            String roomCode,
            BiConsumer<String, JsonObject> onEntry
    ) {
        if (!session.isReady()
                || roomCode == null
                || roomCode.isBlank()) {
            return;
        }

        stream.stop();
        seenKeys.clear();

        stream.listen(
                Constants.FIREBASE_DATABASE_URL,
                "/rooms/"
                        + roomCode
                        + "/"
                        + channelName,
                session.idToken(),
                raw -> handleEvent(
                        raw,
                        onEntry
                )
        );
    }

    private void handleEvent(
            String raw,
            BiConsumer<String, JsonObject> onEntry
    ) {
        try {
            JsonObject event =
                    JsonParser.parseString(raw)
                            .getAsJsonObject();

            String path =
                    event.has("path")
                            ? event.get("path")
                                    .getAsString()
                            : "/";

            JsonElement data =
                    event.get("data");

            if (data == null
                    || data.isJsonNull()) {
                return;
            }

            /*
             * Firebase sends the existing channel contents
             * when the stream first connects.
             */
            if ("/".equals(path)) {

                if (!data.isJsonObject()) {
                    return;
                }

                JsonObject all =
                        data.getAsJsonObject();

                for (String key : all.keySet()) {
                    emit(
                            key,
                            all.get(key),
                            onEntry
                    );
                }

                return;
            }

            /*
             * Firebase sends paths such as:
             * /-OABC123
             */
            String key =
                    path.startsWith("/")
                            ? path.substring(1)
                            : path;

            /*
             * Ignore nested updates unless the path points
             * directly at a message entry.
             */
            if (key.contains("/")) {
                return;
            }

            emit(
                    key,
                    data,
                    onEntry
            );

        } catch (Exception e) {
            WorldGateMod.LOGGER.error(
                    "WorldGate {} realtime parse error",
                    channelName,
                    e
            );
        }
    }

    private void emit(
            String key,
            JsonElement element,
            BiConsumer<String, JsonObject> onEntry
    ) {
        if (key == null
                || key.isBlank()
                || element == null
                || element.isJsonNull()
                || !element.isJsonObject()) {
            return;
        }

        /*
         * A Firebase POST key identifies one message.
         * Do not display the same message twice.
         */
        if (!seenKeys.add(key)) {
            return;
        }

        JsonObject object =
                element.getAsJsonObject();

        String uid =
                object.has("uid")
                        ? object.get("uid").getAsString()
                        : "?";

        onEntry.accept(
                uid,
                object
        );
    }

    public void stopListening() {
        stream.stop();
        seenKeys.clear();
    }
}
