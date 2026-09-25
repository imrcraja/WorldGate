package com.rcraja.worldgate.client;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Host-owned permissions that survive room-code changes, host reconnects and
 * Minecraft world reconnects. The list stays on the host device.
 */
public final class HostPermissionManager {
    private static final String KEY = "hostOpPlayers";
    private static final Set<UUID> OP_PLAYERS = ConcurrentHashMap.newKeySet();

    private HostPermissionManager() {}

    public static void load() {
        OP_PLAYERS.clear();
        String raw = LocalWorldGateData.get(KEY);
        if (raw == null || raw.isBlank()) return;
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .forEach(s -> {
                    try { OP_PLAYERS.add(UUID.fromString(s)); }
                    catch (IllegalArgumentException ignored) {}
                });
    }

    public static boolean contains(UUID uuid) {
        return uuid != null && OP_PLAYERS.contains(uuid);
    }

    public static void grant(UUID uuid) {
        if (uuid == null) return;
        OP_PLAYERS.add(uuid);
        persist();
    }

    public static void revoke(UUID uuid) {
        if (uuid == null) return;
        OP_PLAYERS.remove(uuid);
        persist();
    }

    public static Set<UUID> snapshot() {
        return Set.copyOf(OP_PLAYERS);
    }

    private static void persist() {
        String raw = OP_PLAYERS.stream()
                .map(UUID::toString)
                .sorted()
                .collect(Collectors.joining(","));
        LocalWorldGateData.set(KEY, raw);
    }
}
