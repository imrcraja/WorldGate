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
                || roomCode.isBlank()
                || payload == null) {
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
                || roomCode.isBlank()
                || onEntry == null) {
            return;
        }

        stream.stop();

        /*
         * Do not clear seenKeys here.
         *
         * Firebase may reconnect and send the existing
         * messages again. Keeping the keys prevents the
         * same chat/emote from appearing twice.
         */
        stream.listen(
                Constants.FIREBASE_DATABASE_URL,
                "/rooms/"
                        + roomCode
                        + "/"
                        + channelName,
                session.idToken(),
                raw ->
                        handleEvent(
                                raw,
                                onEntry
                        )
        );
    }

    private void handleEvent(
            String raw,
            BiConsumer<String, JsonObject> onEntry
    ) {

        if (raw == null
                || raw.isBlank()) {
            return;
        }

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
             * Initial Firebase snapshot.
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
             * Firebase child event:
             * /-OABC123
             */
            String key =
                    path.startsWith("/")
                            ? path.substring(1)
                            : path;

            /*
             * Ignore nested child updates.
             * Chat/emote entries are immutable POST objects.
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
         * Prevent duplicate delivery after:
         * - Firebase reconnect
         * - initial snapshot
         * - repeated server events
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
