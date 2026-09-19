package com.rcraja.worldgate.network;

import com.rcraja.worldgate.WorldGateMod;

import java.util.Random;

/**
 * Creates/looks up rooms in Realtime Database under /rooms/<code>.
 * hostAddress/hostPort should be the mod's own UPnP-forwarded address once
 * that networking layer is built; for now this only handles the Firebase
 * bookkeeping side (this is the piece that's genuinely still a TODO --
 * actually connecting the joining player's game to the host is real
 * socket/UPnP work, not just a REST call).
 */
public class RoomManager {
    private final FirebaseSession session;

    public RoomManager(FirebaseSession session) {
        this.session = session;
    }

    public String createRoom(String hostAddress, int hostPort) {
        if (!session.isReady()) return null;
        String roomCode = generateRoomCode();
        String json = "{"
                + "\"hostUid\":\"" + session.uid() + "\","
                + "\"hostAddress\":\"" + hostAddress + "\","
                + "\"hostPort\":" + hostPort + ","
                + "\"createdAt\":" + System.currentTimeMillis()
                + "}";
        String result = session.db().put("/rooms/" + roomCode, json);
        WorldGateMod.LOGGER.info("WorldGate room created: {} -> {}", roomCode, result);
        return roomCode;
    }

    public String getRoom(String roomCode) {
        if (!session.isReady()) return null;
        return session.db().get("/rooms/" + roomCode);
    }

    private String generateRoomCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
