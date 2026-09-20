package com.rcraja.worldgate.client.gui;

/**
 * Ping thresholds for the colored Tab list, per RC RAJA's spec:
 * white = good, yellow = slightly bad, orange = worse, red = very bad.
 * Pure logic, no Minecraft classes involved, so it's shared across every
 * version module. Adjust the millisecond breakpoints below to taste.
 */
public enum PingColor {
    WHITE(0xFFFFFF, 0, 75),
    YELLOW(0xFFFF55, 75, 150),
    ORANGE(0xFFAA00, 150, 300),
    RED(0xFF5555, 300, Integer.MAX_VALUE);

    public final int color;
    public final int minMs;
    public final int maxMs;

    PingColor(int color, int minMs, int maxMs) {
        this.color = color;
        this.minMs = minMs;
        this.maxMs = maxMs;
    }

    public static PingColor forPing(int pingMs) {
        for (PingColor p : values()) {
            if (pingMs >= p.minMs && pingMs < p.maxMs) return p;
        }
        return RED;
    }
}
