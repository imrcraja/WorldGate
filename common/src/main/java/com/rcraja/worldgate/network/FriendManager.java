package com.rcraja.worldgate.network;

import com.rcraja.worldgate.WorldGateMod;

/**
 * Firebase-backed friends: send a request by pasting a friend's UID
 * (shown to them in the WorldGate screen so they can share it), then they
 * accept or reject it. Username-based lookup (instead of raw UIDs) is a
 * good next step once there's an account/profile system.
 */
public class FriendManager {
    private final FirebaseSession session;

    public FriendManager(FirebaseSession session) {
        this.session = session;
    }

    public String myUid() {
        return session.uid();
    }

    public boolean sendRequest(String targetUid) {
        if (!session.isReady() || targetUid == null || targetUid.isBlank()) return false;
        String path = "/friend_requests/" + targetUid.trim() + "/" + session.uid();
        String json = "{\"fromUid\":\"" + session.uid() + "\",\"sentAt\":" + System.currentTimeMillis() + "}";
        String result = session.db().put(path, json);
        WorldGateMod.LOGGER.info("WorldGate friend request sent to {}: {}", targetUid, result);
        return result != null;
    }

    public String getIncomingRequests() {
        if (!session.isReady()) return null;
        return session.db().get("/friend_requests/" + session.uid());
    }

    public boolean acceptRequest(String fromUid) {
        if (!session.isReady() || fromUid == null || fromUid.isBlank()) return false;
        fromUid = fromUid.trim();
        session.db().put("/friends/" + session.uid() + "/" + fromUid, "true");
        session.db().put("/friends/" + fromUid + "/" + session.uid(), "true");
        session.db().delete("/friend_requests/" + session.uid() + "/" + fromUid);
        return true;
    }

    public boolean rejectRequest(String fromUid) {
        if (!session.isReady() || fromUid == null || fromUid.isBlank()) return false;
        session.db().delete("/friend_requests/" + session.uid() + "/" + fromUid.trim());
        return true;
    }

    public String getFriends() {
        if (!session.isReady()) return null;
        return session.db().get("/friends/" + session.uid());
    }
}
