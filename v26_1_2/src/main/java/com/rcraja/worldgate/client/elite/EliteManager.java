package com.rcraja.worldgate.client.elite;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rcraja.worldgate.client.WorldGateModClient;
import com.rcraja.worldgate.network.FirebaseDatabaseClient;
import com.rcraja.worldgate.network.FirebaseSession;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Client-side read model for WorldGate Elite.
 *
 * The server/backend remains authoritative for level and eligible spending.
 * The client only reads the published profile data and never calculates
 * entitlement from local purchases.
 */
public final class EliteManager {
    private static final Map<String, EliteProfile> CACHE = new ConcurrentHashMap<>();

    private EliteManager() {}

    public static EliteProfile loadOwnProfile() {
        FirebaseSession session = WorldGateModClient.SESSION;
        if (!session.isReady() || session.uid() == null || session.uid().isBlank()) {
            return EliteProfile.unavailable();
        }

        String uid = session.uid();
        EliteProfile cached = CACHE.get(uid);
        if (cached != null) {
            return cached;
        }

        FirebaseDatabaseClient db = session.db();
        String raw = db.get("/elite/users/" + uid);
        EliteProfile profile = parse(uid, raw);
        CACHE.put(uid, profile);
        return profile;
    }

    public static void refreshOwnProfile() {
        FirebaseSession session = WorldGateModClient.SESSION;
        if (!session.isReady() || session.uid() == null || session.uid().isBlank()) {
            return;
        }

        String uid = session.uid();
        String raw = session.db().get("/elite/users/" + uid);
        CACHE.put(uid, parse(uid, raw));
    }

    public static EliteProfile loadProfile(String uid) {
        if (uid == null || uid.isBlank()) {
            return EliteProfile.unavailable();
        }

        EliteProfile cached = CACHE.get(uid);
        if (cached != null) {
            return cached;
        }

        FirebaseSession session = WorldGateModClient.SESSION;
        if (!session.isReady()) {
            return EliteProfile.unavailable();
        }

        String raw = session.db().get("/elite/users/" + uid);
        EliteProfile profile = parse(uid, raw);
        CACHE.put(uid, profile);
        return profile;
    }

    private static EliteProfile parse(String uid, String raw) {
        if (raw == null || raw.isBlank() || "null".equals(raw)) {
            return EliteProfile.unavailable(uid);
        }

        try {
            JsonObject o = JsonParser.parseString(raw).getAsJsonObject();

            int level = o.has("level") ? Math.max(0, o.get("level").getAsInt()) : 0;
            long spent = o.has("eligibleSpentMinorUnits")
                    ? Math.max(0L, o.get("eligibleSpentMinorUnits").getAsLong())
                    : 0L;
            long next = o.has("nextLevelThresholdMinorUnits")
                    ? Math.max(0L, o.get("nextLevelThresholdMinorUnits").getAsLong())
                    : 0L;
            long max = o.has("maxLevelThresholdMinorUnits")
                    ? Math.max(0L, o.get("maxLevelThresholdMinorUnits").getAsLong())
                    : 0L;

            return new EliteProfile(uid, level, spent, next, max, true);
        } catch (Exception ignored) {
            return EliteProfile.unavailable(uid);
        }
    }

    public record EliteProfile(
            String uid,
            int level,
            long eligibleSpentMinorUnits,
            long nextLevelThresholdMinorUnits,
            long maxLevelThresholdMinorUnits,
            boolean available
    ) {
        public static EliteProfile unavailable() {
            return unavailable(null);
        }

        public static EliteProfile unavailable(String uid) {
            return new EliteProfile(uid, 0, 0L, 0L, 0L, false);
        }

        public boolean hasElite() {
            return available && level > 0;
        }

        public long remainingToNext() {
            if (!available || nextLevelThresholdMinorUnits <= 0L) {
                return 0L;
            }
            return Math.max(0L, nextLevelThresholdMinorUnits - eligibleSpentMinorUnits);
        }

        public long remainingToMax() {
            if (!available || maxLevelThresholdMinorUnits <= 0L) {
                return 0L;
            }
            return Math.max(0L, maxLevelThresholdMinorUnits - eligibleSpentMinorUnits);
        }

        public String displayLevel() {
            return hasElite() ? "Elite " + level : "Elite";
        }
    }
}
