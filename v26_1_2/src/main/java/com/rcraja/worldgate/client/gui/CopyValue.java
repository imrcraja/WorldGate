package com.rcraja.worldgate.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class CopyValue {
    private CopyValue() {}

    public static boolean copy(String value, String label) {
        String safe = value == null ? "" : value.trim();
        if (safe.isBlank()) return false;
        try {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.keyboardHandler.setClipboard(safe);
            if (minecraft.player != null) {
                minecraft.player.sendOverlayMessage(
                        Component.literal("WorldGate: " + label + " copied.")
                );
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
