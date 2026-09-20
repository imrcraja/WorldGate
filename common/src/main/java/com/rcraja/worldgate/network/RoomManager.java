package com.rcraja.worldgate.network;

import com.rcraja.worldgate.Constants;
import com.rcraja.worldgate.WorldGateMod;

import java.util.Random;
import java.util.function.Consumer;

public class RoomManager {

    private final FirebaseSession session;
    private final FirebaseStreamClient roomStream =
            new FirebaseStreamClient();

    private volatile Consumer<String> roomChanged;

    public RoomManager(FirebaseSession session) {
        this.session = session;
    }

    public String createRoom(String hostAddress, int hostPort) {
        if (!session.isReady()) return null;

        String roomCode = generateRoomCode();

        String json = "{"
                + "\"hostUid\":\"" + session.uid() + "\","
                + "\"hostAddress\":\"" + escape(hostAddress) + "\","
                + "\"hostPort\":" + hostPort + ","
                + "\"status\":\"waiting\","
                + "\"host\":{\"online\":true,\"lastSeen\":"
                + System.currentTimeMillis() + "},"
                + "\"player\":{\"online\":false},"
                + "\"createdAt\":" + System.currentTimeMillis()
                + "}";

        String result =
                session.db().put(
                        "/rooms/" + roomCode,
                        json
                );

        if (result == null) {
            WorldGateMod.LOGGER.error(
                    "WorldGate room creation failed: {}",
                    roomCode
            );
            return null;
        }

        WorldGateMod.LOGGER.info(
                "WorldGate room created: {}",
                roomCode
        );

        return roomCode;
    }

    public String getRoom(String roomCode) {
        if (!session.isReady()) return null;

        return session.db().get(
                "/rooms/" + roomCode
        );
    }

    public boolean hostHeartbeat(String roomCode) {
        if (!session.isReady()
                || roomCode == null) {
            return false;
        }

        String json = "{"
                + "\"online\":true,"
                + "\"lastSeen\":"
                + System.currentTimeMillis()
                + "}";

        return session.db().patch(
                "/rooms/" + roomCode + "/host",
                json
        ) != null;
    }

    public boolean playerJoin(
            String roomCode,
            String ign
    ) {
        if (!session.isReady()
                || roomCode == null) {
            return false;
        }

        String safeIgn =
                ign == null
                        ? "Unknown"
                        : escape(ign);

        String json = "{"
                + "\"uid\":\""
                + escape(session.uid())
                + "\","
                + "\"ign\":\""
                + safeIgn
                + "\","
                + "\"online\":true,"
                + "\"lastSeen\":"
                + System.currentTimeMillis()
                + "}";

        String result =
                session.db().patch(
                        "/rooms/" + roomCode,
                        "{"
                                + "\"status\":\"connected\","
                                + "\"player\":"
                                + json
                                + "}"
                );

        return result != null;
    }

    public boolean playerHeartbeat(String roomCode) {
        if (!session.isReady()
                || roomCode == null) {
            return false;
        }

        String json = "{"
                + "\"online\":true,"
                + "\"lastSeen\":"
                + System.currentTimeMillis()
                + "}";

        return session.db().patch(
                "/rooms/" + roomCode + "/player",
                json
        ) != null;
    }

    public boolean playerLeave(String roomCode) {
        if (!session.isReady()
                || roomCode == null) {
            return false;
        }

        String json = "{"
                + "\"online\":false,"
                + "\"lastSeen\":"
                + System.currentTimeMillis()
                + "}";

        return session.db().patch(
                "/rooms/" + roomCode,
                "{"
                        + "\"status\":\"waiting\","
                        + "\"player\":"
                        + json
                        + "}"
        ) != null;
    }

    public boolean hostLeave(String roomCode) {
        if (!session.isReady()
                || roomCode == null) {
            return false;
        }

        session.db().delete(
                "/rooms/" + roomCode
        );

        return true;
    }

    public void setRoomChangedListener(
            Consumer<String> listener
    ) {
        roomChanged = listener;
    }

    public void startRealtime(String roomCode) {

        if (!session.isReady()
                || roomCode == null
                || roomCode.isBlank()) {
            return;
        }

        roomStream.stop();

        roomStream.listen(
                Constants.FIREBASE_DATABASE_URL,
                "/rooms/" + roomCode,
                session.idToken(),
                data -> {

                    Consumer<String> listener =
                            roomChanged;

                    if (listener != null) {
                        listener.accept(data);
                    }
                }
        );
    }

    public void stopRealtime() {
        roomStream.stop();
    }

    private static String escape(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private String generateRoomCode() {

        String chars =
                "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

        Random rnd =
                new Random();

        StringBuilder sb =
                new StringBuilder();

        for (int i = 0; i < 6; i++) {
            sb.append(
                    chars.charAt(
                            rnd.nextInt(chars.length())
                    )
            );
        }

        return sb.toString();
    }
}
