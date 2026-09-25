package com.rcraja.worldgate.network;

import com.rcraja.worldgate.Constants;
import com.rcraja.worldgate.WorldGateMod;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public class RoomManager {

    private final FirebaseSession session;

    private final FirebaseStreamClient roomStream = new FirebaseStreamClient();
    private final FirebaseStreamClient inviteStream = new FirebaseStreamClient();
    private volatile Consumer<String> roomChanged;
    private volatile Consumer<String> inviteChanged;

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

    public boolean inviteFriend(String roomCode, String friendUid, String hostName) {
        if (!session.isReady() || roomCode == null || roomCode.isBlank()
                || friendUid == null || friendUid.isBlank() || friendUid.equals(session.uid())) return false;
        String safeName = hostName == null || hostName.isBlank() ? "Player" : escape(hostName.trim());
        String json = "{"
                + "\"roomCode\":\"" + escape(roomCode.trim().toUpperCase()) + "\","
                + "\"fromUid\":\"" + escape(session.uid()) + "\","
                + "\"fromName\":\"" + safeName + "\","
                + "\"sentAt\":" + System.currentTimeMillis() + "}";
        return session.db().put("/room_invites/" + friendUid.trim() + "/" + session.uid(), json) != null;
    }

    public String getIncomingInvites() {
        return session.isReady() ? session.db().get("/room_invites/" + session.uid()) : null;
    }

    public boolean removeInvite(String fromUid) {
        if (!session.isReady() || fromUid == null || fromUid.isBlank()) return false;
        session.db().delete("/room_invites/" + session.uid() + "/" + fromUid.trim());
        return true;
    }

    public void setInviteChangedListener(Consumer<String> listener) { inviteChanged = listener; }

    public void startInviteRealtime() {
        if (!session.isReady()) return;
        inviteStream.stop();
        inviteStream.listen(Constants.FIREBASE_DATABASE_URL, "/room_invites/" + session.uid(), session.idToken(), data -> {
            Consumer<String> listener = inviteChanged;
            if (listener != null) listener.accept(data);
        });
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

        String roomJson = session.db().get("/rooms/" + roomCode);
        if (roomJson == null || roomJson.isBlank() || "null".equals(roomJson)) {
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

        String roomJson = session.db().get("/rooms/" + roomCode);
        if (roomJson == null || roomJson.isBlank() || "null".equals(roomJson)) {
            return false;
        }

        String expectedHost = "\"hostUid\":\"" + escape(session.uid()) + "\"";
        if (!roomJson.contains(expectedHost)) {
            WorldGateMod.LOGGER.warn(
                    "WorldGate rejected non-host room deletion for {}",
                    roomCode
            );
            return false;
        }

        session.db().delete("/rooms/" + roomCode);
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

    public void stopInviteRealtime() {
        inviteStream.stop();
    }

    public void stopRealtime() {
        roomStream.stop();
        inviteStream.stop();
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

    /**
     * Generates a numeric room code and avoids an already-existing room.
     *
     * The code starts at 5 digits and can grow through 6, 7, 8, 9 and finally
     * 10 digits when the shorter namespaces are exhausted. Existing rooms are
     * checked in Firebase before a code is returned.
     */
    private String generateRoomCode() {
        for (int length = 5; length <= 10; length++) {
            int attempts = length <= 8 ? 80 : 160;

            for (int attempt = 0; attempt < attempts; attempt++) {
                String code = randomNumericCode(length);

                String existing = session.db().get("/rooms/" + code);
                if (existing == null || existing.isBlank() || "null".equals(existing)) {
                    return code;
                }
            }
        }

        WorldGateMod.LOGGER.error("WorldGate could not find a free numeric room code.");
        return null;
    }

    private static String randomNumericCode(int length) {
        StringBuilder code = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            int digit = ThreadLocalRandom.current().nextInt(i == 0 ? 1 : 0, 10);
            code.append(digit);
        }

        return code.toString();
    }
}
