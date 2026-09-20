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

    public String createRoom(
            String hostAddress,
            int hostPort
    ) {
        if (!session.isReady()) {
            return null;
        }

        String roomCode =
                generateRoomCode();

        long now =
                System.currentTimeMillis();

        String json =
                "{"
                + "\"hostUid\":\""
                + escape(session.uid())
                + "\","
                + "\"hostAddress\":\""
                + escape(hostAddress)
                + "\","
                + "\"hostPort\":"
                + hostPort
                + ","
                + "\"status\":\"waiting\","
                + "\"host\":{"
                + "\"online\":true,"
                + "\"lastSeen\":"
                + now
                + "},"
                + "\"players\":{},"
                + "\"createdAt\":"
                + now
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

    public String getRoom(
            String roomCode
    ) {
        if (!session.isReady()
                || roomCode == null
                || roomCode.isBlank()) {
            return null;
        }

        return session.db().get(
                "/rooms/" + roomCode
        );
    }

    public boolean hostHeartbeat(
            String roomCode
    ) {
        if (!session.isReady()
                || roomCode == null
                || roomCode.isBlank()) {
            return false;
        }

        long now =
                System.currentTimeMillis();

        boolean hostOk =
                session.db().patch(
                        "/rooms/"
                                + roomCode
                                + "/host",
                        "{"
                                + "\"online\":true,"
                                + "\"lastSeen\":"
                                + now
                                + "}"
                ) != null;

        boolean playerOk =
                updatePlayerHeartbeat(
                        roomCode,
                        now
                );

        return hostOk && playerOk;
    }

    public boolean playerJoin(
            String roomCode,
            String ign
    ) {
        if (!session.isReady()
                || roomCode == null
                || roomCode.isBlank()) {
            return false;
        }

        String safeIgn =
                ign == null || ign.isBlank()
                        ? "Unknown"
                        : escape(ign.trim());

        long now =
                System.currentTimeMillis();

        String json =
                "{"
                + "\"uid\":\""
                + escape(session.uid())
                + "\","
                + "\"ign\":\""
                + safeIgn
                + "\","
                + "\"online\":true,"
                + "\"lastSeen\":"
                + now
                + "}";

        String result =
                session.db().put(
                        "/rooms/"
                                + roomCode
                                + "/players/"
                                + session.uid(),
                        json
                );

        if (result == null) {
            WorldGateMod.LOGGER.error(
                    "WorldGate player join failed for room {}",
                    roomCode
            );

            return false;
        }

        session.db().patch(
                "/rooms/" + roomCode,
                "{\"status\":\"connected\"}"
        );

        return true;
    }

    public boolean playerHeartbeat(
            String roomCode
    ) {
        if (!session.isReady()
                || roomCode == null
                || roomCode.isBlank()) {
            return false;
        }

        return updatePlayerHeartbeat(
                roomCode,
                System.currentTimeMillis()
        );
    }

    private boolean updatePlayerHeartbeat(
            String roomCode,
            long now
    ) {
        String json =
                "{"
                + "\"online\":true,"
                + "\"lastSeen\":"
                + now
                + "}";

        return session.db().patch(
                "/rooms/"
                        + roomCode
                        + "/players/"
                        + session.uid(),
                json
        ) != null;
    }

    public boolean playerLeave(
            String roomCode
    ) {
        if (!session.isReady()
                || roomCode == null
                || roomCode.isBlank()) {
            return false;
        }

        String json =
                "{"
                + "\"online\":false,"
                + "\"lastSeen\":"
                + System.currentTimeMillis()
                + "}";

        return session.db().patch(
                "/rooms/"
                        + roomCode
                        + "/players/"
                        + session.uid(),
                json
        ) != null;
    }

    public boolean hostLeave(
            String roomCode
    ) {
        if (!session.isReady()
                || roomCode == null
                || roomCode.isBlank()) {
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

    public void startRealtime(
            String roomCode
    ) {
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

    private static String escape(
            String value
    ) {
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

        Random random =
                new Random();

        StringBuilder code =
                new StringBuilder(6);

        for (int i = 0; i < 6; i++) {
            code.append(
                    chars.charAt(
                            random.nextInt(
                                    chars.length()
                            )
                    )
            );
        }

        return code.toString();
    }
}
