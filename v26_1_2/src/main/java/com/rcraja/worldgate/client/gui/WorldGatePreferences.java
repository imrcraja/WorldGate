package com.rcraja.worldgate.client.gui;

import java.util.prefs.Preferences;

/**
 * Small client-only preferences for optional WorldGate presentation controls.
 * These never modify vanilla Minecraft controls or gameplay state.
 */
public final class WorldGatePreferences {
    private static final Preferences PREFS =
            Preferences.userNodeForPackage(WorldGatePreferences.class);

    private WorldGatePreferences() {}

    public static boolean overlayEnabled() {
        return PREFS.getBoolean("overlayEnabled", true);
    }

    public static void setOverlayEnabled(boolean enabled) {
        PREFS.putBoolean("overlayEnabled", enabled);
    }
}
