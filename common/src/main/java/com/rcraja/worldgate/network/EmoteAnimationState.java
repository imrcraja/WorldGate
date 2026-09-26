package com.rcraja.worldgate.network;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Small client-side timing/state bridge used by the 26.1.2 player renderer. */
public final class EmoteAnimationState {
    private static final long DEFAULT_DURATION_MS = 1800L;
    private static final ConcurrentHashMap<UUID, Active> ACTIVE = new ConcurrentHashMap<>();

    private EmoteAnimationState() {}

    public static void play(UUID playerId, String emote) {
        if (playerId == null || emote == null || emote.isBlank()) return;
        ACTIVE.put(playerId, new Active(emote, System.currentTimeMillis(), DEFAULT_DURATION_MS));
    }

    public static Active get(UUID playerId) {
        Active value = ACTIVE.get(playerId);
        if (value == null) return null;
        if (value.isExpired()) {
            ACTIVE.remove(playerId, value);
            return null;
        }
        return value;
    }

    public static void clear(UUID playerId) {
        if (playerId != null) ACTIVE.remove(playerId);
    }

    public record Active(String name, long startedAt, long durationMs) {
        public boolean isExpired() {
            return System.currentTimeMillis() - startedAt >= durationMs;
        }
        public float progress() {
            long elapsed = Math.max(0L, System.currentTimeMillis() - startedAt);
            return Math.min(1.0f, elapsed / (float) durationMs);
        }
    }
}
