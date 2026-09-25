package com.rcraja.worldgate.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rcraja.worldgate.Constants;
import com.rcraja.worldgate.WorldGateMod;
import com.rcraja.worldgate.client.UserProfileCache;

import java.util.Map;
import java.util.UUID;
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
    private volatile String myFriendCode;

    public FriendManager(FirebaseSession session) {
        this.session = session;
    }

    public String myUid() {
        return session.uid();
    }

    /**
     * Returns the short Friend Code.
     * The code is stored permanently in Firebase.
     */
    public String myFriendCode() {

        if (!session.isReady()) {
            return null;
        }

        String uid = session.uid();

        if (uid == null || uid.isBlank()) {
            return null;
        }

        if (myFriendCode != null) {
            return myFriendCode;
        }

        String existing = UserProfileCache.value("friendCode", null);
        if (existing == null || existing.isBlank()) {
            existing = session.db().get(
                    "/profiles/" + uid + "/friendCode"
            );
            if (existing != null && !existing.equals("null")) {
                try {
                    JsonObject cachedProfile = JsonParser.parseString(existing).getAsJsonObject();
                    existing = cachedProfile.has("friendCode") ? cachedProfile.get("friendCode").getAsString() : null;
                } catch (Exception ignored) {
                }
            }
        }

        if (existing != null
                && !existing.equals("null")
                && existing.startsWith("\"")
                && existing.endsWith("\"")) {

            myFriendCode =
                    existing.substring(
                            1,
                            existing.length() - 1
                    );

            /*
             * Make sure the Friend Code -> UID mapping
             * also exists.
             */
            session.db().put(
                    "/friend_codes/" + myFriendCode,
                    "\"" + uid + "\""
            );

            return myFriendCode;
        }

        if (existing != null && !existing.isBlank() && !existing.equals("null")) {
            myFriendCode = existing.trim();
            session.db().put(
                    "/friend_codes/" + myFriendCode,
                    "\"" + uid + "\""
            );
            return myFriendCode;
        }

        String code = generateFriendCode();

        session.db().put(
                "/profiles/" + uid + "/friendCode",
                "\"" + code + "\""
        );

        session.db().put(
                "/friend_codes/" + code,
                "\"" + uid + "\""
        );

        myFriendCode = code;

        return code;
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

        String code = myFriendCode();

        if (code == null) {
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
                        + "\"friendCode\":\""
                        + escapeJson(code)
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

    /**
     * Mark this player online.
     */
    public void setOnline(String displayName) {

        if (!session.isReady()) {
            return;
        }

        updateMyProfile(displayName);

        String uid = session.uid();

        if (uid != null) {
            session.db().patch(
                    "/profiles/" + uid,
                    "{"
                            + "\"online\":true,"
                            + "\"lastSeen\":"
                            + System.currentTimeMillis()
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
                            + "}"
            );
        }
    }

    /**
     * Friend Code -> UID lookup.
     */
    private String uidFromFriendCode(String code) {

        if (!session.isReady()
                || code == null
                || code.isBlank()) {
            return null;
        }

        String value =
                session.db().get(
                        "/friend_codes/"
                                + code.trim().toUpperCase()
                );

        if (value == null || value.equals("null")) {
            return null;
        }

        if (value.startsWith("\"")
                && value.endsWith("\"")) {

            return value.substring(
                    1,
                    value.length() - 1
            );
        }

        return value;
    }

    /**
     * Send friend request using Friend Code.
     */
    public boolean sendRequestByCode(String friendCode) {

        if (!session.isReady()
                || friendCode == null
                || friendCode.isBlank()) {
            return false;
        }

        String code =
                friendCode.trim().toUpperCase();

        String targetUid =
                uidFromFriendCode(code);

        if (targetUid == null
                || targetUid.isBlank()
                || targetUid.equals(session.uid())) {
            return false;
        }

        String senderName =
                getMyDisplayName();

        String json =
                "{"
                        + "\"fromUid\":\""
                        + escapeJson(session.uid())
                        + "\","
                        + "\"fromFriendCode\":\""
                        + escapeJson(myFriendCode())
                        + "\","
                        + "\"fromName\":\""
                        + escapeJson(senderName)
                        + "\","
                        + "\"sentAt\":"
                        + System.currentTimeMillis()
                        + "}";

        String result =
                session.db().put(
                        "/friend_requests/"
                                + targetUid
                                + "/"
                                + session.uid(),
                        json
                );

        return result != null;
    }

    /**
     * Compatibility method for old code.
     */
    public boolean sendRequest(String targetUid) {

        if (!session.isReady()
                || targetUid == null
                || targetUid.isBlank()) {
            return false;
        }

        String json =
                "{"
                        + "\"fromUid\":\""
                        + escapeJson(session.uid())
                        + "\","
                        + "\"fromFriendCode\":\""
                        + escapeJson(myFriendCode())
                        + "\","
                        + "\"fromName\":\""
                        + escapeJson(getMyDisplayName())
                        + "\","
                        + "\"sentAt\":"
                        + System.currentTimeMillis()
                        + "}";

        String result =
                session.db().put(
                        "/friend_requests/"
                                + targetUid.trim()
                                + "/"
                                + session.uid(),
                        json
                );

        return result != null;
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

    private String generateFriendCode() {

        String uid = session.uid();

        if (uid == null || uid.isBlank()) {

            return "WG"
                    + UUID.randomUUID()
                            .toString()
                            .replace("-", "")
                            .substring(0, 6)
                            .toUpperCase();
        }

        String raw =
                uid.replace("-", "")
                        .toUpperCase();

        return "WG"
                + raw.substring(
                        0,
                        Math.min(6, raw.length())
                );
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
