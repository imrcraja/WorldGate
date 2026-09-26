package com.rcraja.worldgate.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rcraja.worldgate.Constants;
import com.rcraja.worldgate.WorldGateMod;
import com.rcraja.worldgate.client.UserProfileCache;

import java.util.Map;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class FriendManager {

    private final FirebaseSession session;

    private final FirebaseStreamClient requestStream =
            new FirebaseStreamClient();

    private final FirebaseStreamClient friendsStream =
            new FirebaseStreamClient();

    private final FirebaseStreamClient ownProfileStream =
            new FirebaseStreamClient();

    private final Map<String, FirebaseStreamClient> profileStreams =
            new ConcurrentHashMap<>();

    private volatile Consumer<String> friendListChanged;
    private volatile String myPublicId;

    public FriendManager(FirebaseSession session) {
        this.session = session;
    }

    public String myUid() {
        return session.uid();
    }

    /**
     * Stable public numeric identity. Firebase UID remains private/internal.
     * The displayed ID is always 12 digits (within the requested 7-12 range).
     */
    public String myPublicId() {
        if (!session.isReady()) return null;
        String uid = session.uid();
        if (uid == null || uid.isBlank()) return null;
        if (myPublicId != null) return myPublicId;

        String cached = UserProfileCache.value("publicId", null);
        if (cached != null && cached.matches("\\d{7,12}")) {
            myPublicId = cached;
            return cached;
        }

        String existing = session.db().get("/profiles/" + uid + "/publicId");
        if (existing != null && !existing.equals("null")) {
            try {
                JsonObject o = JsonParser.parseString(existing).getAsJsonObject();
                if (o.has("publicId")) existing = o.get("publicId").getAsString();
            } catch (Exception ignored) { }
        }
        if (existing != null && existing.matches("\\d{7,12}")) {
            myPublicId = existing;
            return existing;
        }

        String id = generatePublicId(uid);
        for (int attempt = 0; attempt < 10; attempt++) {
            String owner = session.db().get("/public_ids/" + id);
            if (owner == null || owner.equals("null") || owner.equals("\"" + uid + "\"")) break;
            id = incrementPublicId(id);
        }
        session.db().put("/profiles/" + uid + "/publicId", "\"" + id + "\"");
        session.db().put("/public_ids/" + id, "\"" + uid + "\"");
        myPublicId = id;
        return id;
    }

    /** Compatibility alias: old callers now receive the numeric public ID. */
    public String myFriendCode() {
        return myPublicId();
    }

    /**
     * Creates/updates the user's profile.
     */
    public boolean updateMyProfile(String displayName) {

        if (!session.isReady()) {
            return false;
        }

        String uid = session.uid();

        if (uid == null || uid.isBlank()) {
            return false;
        }

        String publicId = myPublicId();

        if (publicId == null) {
            return false;
        }

        String safeName = escapeJson(
                displayName == null || displayName.isBlank()
                        ? "Player"
                        : displayName.trim()
        );

        String json =
                "{"
                        + "\"uid\":\""
                        + escapeJson(uid)
                        + "\","
                        + "\"publicId\":\""
                        + escapeJson(publicId)
                        + "\","
                        + "\"displayName\":\""
                        + safeName
                        + "\","
                        + "\"online\":true,"
                        + "\"lastSeen\":"
                        + System.currentTimeMillis()
                        + "}";

        String result = session.db().put(
                "/profiles/" + uid,
                json
        );
        if (result != null) {
            UserProfileCache.save(json);
        }
        return result != null;
    }

    /** Refresh the profile presence lease without changing profile identity fields. */
    public void refreshOnlinePresence() {
        if (!session.isReady()) return;
        String uid = session.uid();
        if (uid == null || uid.isBlank()) return;
        session.db().patch(
                "/profiles/" + uid,
                "{\"online\":true,\"lastSeen\":" + System.currentTimeMillis() + ",\"onlineUntil\":" + (System.currentTimeMillis() + 30000L) + "}"
        );
    }

    /**
     * Mark this player online.
     */
    public void setOnline(String displayName) {

        if (!session.isReady()) {
            return;
        }

        updateMyProfile(displayName);
        refreshOnlinePresence();

        String uid = session.uid();

        if (uid != null) {
            session.db().patch(
                    "/profiles/" + uid,
                    "{"
                            + "\"online\":true,"
                            + "\"lastSeen\":"
                            + System.currentTimeMillis()
                            + ",\"onlineUntil\":"
                            + (System.currentTimeMillis() + 30000L)
                            + "}"
            );
        }
    }

    /**
     * Mark this player offline.
     */
    public void setOffline() {

        if (!session.isReady()) {
            return;
        }

        String uid = session.uid();

        if (uid != null) {
            session.db().patch(
                    "/profiles/" + uid,
                    "{"
                            + "\"online\":false,"
                            + "\"lastSeen\":"
                            + System.currentTimeMillis()
                            + ",\"onlineUntil\":0"
                            + "}"
            );
        }
    }

    /** Numeric public ID -> private Firebase UID lookup. */
    private String uidFromPublicId(String publicId) {
        if (!session.isReady() || publicId == null || !publicId.matches("\\d{7,12}")) return null;
        String value = session.db().get("/public_ids/" + publicId);
        if (value == null || value.equals("null")) return null;
        if (value.startsWith("\"") && value.endsWith("\"")) return value.substring(1, value.length() - 1);
        return value;
    }

    public String resolvePublicId(String publicId) {
        return uidFromPublicId(publicId);
    }

    /** Send a friend request using the public numeric ID only. */
    public boolean sendRequestByCode(String publicId) {
        return sendRequest(publicId);
    }

    /** Send a friend request using a 7-12 digit public ID. */
    public boolean sendRequest(String publicId) {
        if (!session.isReady() || publicId == null || !publicId.matches("\\d{7,12}")) return false;
        String targetUid = uidFromPublicId(publicId.trim());
        if (targetUid == null || targetUid.isBlank() || targetUid.equals(session.uid())) return false;

        String senderName = getMyDisplayName();
        String senderId = myPublicId();
        String json = "{"
                + "\"fromUid\":\"" + escapeJson(session.uid()) + "\","
                + "\"fromPublicId\":\"" + escapeJson(senderId) + "\","
                + "\"fromName\":\"" + escapeJson(senderName) + "\","
                + "\"sentAt\":" + System.currentTimeMillis() + "}";
        return session.db().put("/friend_requests/" + targetUid + "/" + session.uid(), json) != null;
    }

    public String getIncomingRequests() {

        if (!session.isReady()) {
            return null;
        }

        return session.db().get(
                "/friend_requests/" + session.uid()
        );
    }

    public boolean acceptRequest(String fromUid) {

        if (!session.isReady()
                || fromUid == null
                || fromUid.isBlank()) {
            return false;
        }

        fromUid = fromUid.trim();

        session.db().put(
                "/friends/"
                        + session.uid()
                        + "/"
                        + fromUid,
                "true"
        );

        session.db().put(
                "/friends/"
                        + fromUid
                        + "/"
                        + session.uid(),
                "true"
        );

        session.db().delete(
                "/friend_requests/"
                        + session.uid()
                        + "/"
                        + fromUid
        );

        notifyFriendListChanged();

        return true;
    }

    public boolean rejectRequest(String fromUid) {

        if (!session.isReady()
                || fromUid == null
                || fromUid.isBlank()) {
            return false;
        }

        session.db().delete(
                "/friend_requests/"
                        + session.uid()
                        + "/"
                        + fromUid.trim()
        );

        return true;
    }

    public String getFriends() {

        if (!session.isReady()) {
            return null;
        }

        return session.db().get(
                "/friends/" + session.uid()
        );
    }

    /**
     * Returns a friend's profile JSON.
     */
    public String getProfile(String uid) {

        if (!session.isReady()
                || uid == null
                || uid.isBlank()) {
            return null;
        }

        return session.db().get(
                "/profiles/" + uid
        );
    }

    public boolean updateMySkin(String skinUrl) {
        if (!session.isReady() || skinUrl == null || skinUrl.isBlank()) return false;
        String safe = escapeJson(skinUrl.trim());
        return session.db().patch("/profiles/" + session.uid(), "{\"skinUrl\":\"" + safe + "\"}") != null;
    }

    public String getSkinUrl(String uid) {
        String profile = getProfile(uid);
        if (profile == null || profile.isBlank() || "null".equals(profile)) return null;
        try {
            JsonObject object = JsonParser.parseString(profile).getAsJsonObject();
            return object.has("skinUrl") ? object.get("skinUrl").getAsString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public String getMyDisplayName() {

        String uid = session.uid();

        if (!session.isReady() || uid == null) {
            return "Player";
        }

        String profile = UserProfileCache.raw();
        if (profile == null || profile.isBlank()) {
            profile = session.db().get(
                    "/profiles/" + uid
            );
            if (profile != null && !profile.equals("null")) UserProfileCache.save(profile);
        }

        if (profile == null
                || profile.equals("null")) {
            return "Player";
        }

        try {

            JsonObject object =
                    JsonParser.parseString(profile)
                            .getAsJsonObject();

            if (object.has("displayName")) {
                return object
                        .get("displayName")
                        .getAsString();
            }

        } catch (Exception ignored) {
        }

        return "Player";
    }

    /**
     * Register callback for instant friend-list updates.
     */
    public void setFriendListChangedListener(
            Consumer<String> listener
    ) {
        friendListChanged = listener;
    }

    /**
     * Start realtime friend/request/profile listeners.
     */
    public void startRealtime() {

        if (!session.isReady()) {
            return;
        }

        requestStream.listen(
                Constants.FIREBASE_DATABASE_URL,
                "/friend_requests/" + session.uid(),
                session.idToken(),
                data -> notifyFriendListChanged()
        );

        ownProfileStream.listen(
                Constants.FIREBASE_DATABASE_URL,
                "/profiles/" + session.uid(),
                session.idToken(),
                data -> {
                    if (data != null && !data.isBlank() && !"null".equals(data)) {
                        UserProfileCache.save(data);
                        notifyFriendListChanged();
                    }
                }
        );

        friendsStream.listen(
                Constants.FIREBASE_DATABASE_URL,
                "/friends/" + session.uid(),
                session.idToken(),
                data -> {

                    refreshProfileStreams();

                    notifyFriendListChanged();
                }
        );

        refreshProfileStreams();
    }

    private void refreshProfileStreams() {

        String friends = getFriends();

        if (friends == null
                || friends.equals("null")) {

            stopUnusedProfileStreams();

            return;
        }

        try {

            JsonObject object =
                    JsonParser.parseString(friends)
                            .getAsJsonObject();

            for (String uid : object.keySet()) {

                if (profileStreams.containsKey(uid)) {
                    continue;
                }

                FirebaseStreamClient stream =
                        new FirebaseStreamClient();

                profileStreams.put(
                        uid,
                        stream
                );

                stream.listen(
                        Constants.FIREBASE_DATABASE_URL,
                        "/profiles/" + uid,
                        session.idToken(),
                        data -> notifyFriendListChanged()
                );
            }

            stopUnusedProfileStreams(object);

        } catch (Exception e) {

            WorldGateMod.LOGGER.error(
                    "WorldGate friends realtime refresh failed",
                    e
            );
        }
    }

    private void stopUnusedProfileStreams() {

        for (FirebaseStreamClient stream :
                profileStreams.values()) {

            stream.stop();
        }

        profileStreams.clear();
    }

    private void stopUnusedProfileStreams(
            JsonObject currentFriends
    ) {

        profileStreams.entrySet().removeIf(entry -> {

            if (currentFriends.has(entry.getKey())) {
                return false;
            }

            entry.getValue().stop();

            return true;
        });
    }

    private void notifyFriendListChanged() {

        Consumer<String> listener =
                friendListChanged;

        if (listener != null) {
            listener.accept(
                    getFriends()
            );
        }
    }

    public void stopRealtime() {

        requestStream.stop();

        ownProfileStream.stop();

        friendsStream.stop();

        for (FirebaseStreamClient stream :
                profileStreams.values()) {

            stream.stop();
        }

        profileStreams.clear();
    }

    private String generatePublicId(String uid) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(uid.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            long value = 0L;
            for (int i = 0; i < 8; i++) value = (value << 8) | (digest[i] & 0xFFL);
            value = Math.floorMod(value, 900_000_000_000L) + 100_000_000_000L;
            return Long.toString(value);
        } catch (Exception e) {
            return "100000000000";
        }
    }

    private static String incrementPublicId(String id) {
        try {
            long value = Long.parseLong(id);
            value = value >= 999_999_999_999L ? 100_000_000_000L : value + 1L;
            return Long.toString(value);
        } catch (Exception ignored) {
            return "100000000000";
        }
    }

    private static String escapeJson(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
