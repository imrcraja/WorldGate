package com.rcraja.worldgate.network;

/**
 * Holds the anonymous-auth token + database client for the current game
 * session. Created once in WorldGateModClient and reused by RoomManager,
 * FriendManager, ChatManager and EmoteManager, so everything shares one
 * sign-in.
 */
public class FirebaseSession {
    private final FirebaseAuthClient auth = new FirebaseAuthClient();
    private volatile FirebaseDatabaseClient db;
    private volatile boolean ready = false;

    /** Safe to call from a background thread; blocks on one HTTP request. */
    public synchronized boolean connect() {
        if (ready) return true;
        boolean ok = auth.signInAnonymously();
        if (ok) {
            db = new FirebaseDatabaseClient(auth.getIdToken());
            ready = true;
        }
        return ok;
    }

    public boolean isReady() {
        return ready;
    }

    public FirebaseDatabaseClient db() {
        return db;
    }

    public String uid() {
        return auth.getUid();
    }

    public String idToken() {
        return auth.getIdToken();
    }
}
