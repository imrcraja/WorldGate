package com.rcraja.worldgate.network;

import com.rcraja.worldgate.WorldGateMod;

import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class IntegrityGuard {
    private static volatile boolean disabled;

    private IntegrityGuard() {}

    public static boolean isDisabled() {
        return disabled;
    }

    public static boolean verifyLocalRelease() {
        String expected = System.getenv("WORLDGATE_OFFICIAL_MOD_SHA256");
        if (expected == null || expected.isBlank()) {
            return true;
        }

        String actual = currentArtifactSha256();
        boolean ok = expected.trim().equalsIgnoreCase(actual);
        if (!ok) {
            disabled = true;
            WorldGateMod.LOGGER.error("WorldGate integrity verification failed; multiplayer features are disabled.");
        }
        return ok;
    }

    public static String currentArtifactSha256() {
        try {
            URL location = WorldGateMod.class.getProtectionDomain().getCodeSource().getLocation();
            if (!"file".equalsIgnoreCase(location.getProtocol())) {
                return "";
            }

            java.io.File file = new java.io.File(location.toURI());
            if (!file.isFile()) {
                return "";
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = file.toURI().toURL().openStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            WorldGateMod.LOGGER.warn("WorldGate could not calculate mod integrity hash.", e);
            return "";
        }
    }

    public static void disable(String reason) {
        disabled = true;
        WorldGateMod.LOGGER.error("WorldGate security lock: {}", reason == null ? "integrity check failed" : reason);
    }
}
