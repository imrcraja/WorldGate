package com.rcraja.worldgate.client.elite;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;

/**
 * Lightweight procedural Elite badge renderer.
 * The front identity stays stable while the background palette/layers
 * evolve with the level, so future levels do not require new textures.
 */
public final class EliteBadgeRenderer {
    private static final int[] PALETTES = {
            0xFF5B35D5, 0xFF3F51B5, 0xFF1976D2, 0xFF00897B, 0xFF2E7D32,
            0xFF7B1FA2, 0xFFC2185B, 0xFFD84315, 0xFF6A1B9A, 0xFF4527A0
    };

    private EliteBadgeRenderer() {}

    public static void draw(
            GuiGraphicsExtractor graphics,
            Font font,
            int centerX,
            int top,
            int size,
            int level
    ) {
        int safeLevel = Math.max(1, level);
        int palette = PALETTES[(safeLevel - 1) % PALETTES.length];

        graphics.fill(centerX - size / 2, top, centerX + size / 2, top + size, 0xFF0A0A14);
        graphics.fill(centerX - size / 2 + 2, top + 2, centerX + size / 2 - 2, top + size - 2, palette);

        int inset = Math.max(4, size / 8);
        int inner = palette ^ 0x00202020;
        graphics.fill(
                centerX - size / 2 + inset,
                top + inset,
                centerX + size / 2 - inset,
                top + size - inset,
                0xCC000000 | (inner & 0x00FFFFFF)
        );

        int layers = Math.min(12, 2 + safeLevel);
        for (int i = 0; i < layers; i++) {
            int ring = inset + 2 + i * Math.max(1, size / 32);
            if (ring >= size / 2 - 3) break;
            int alpha = Math.max(0x20, 0xA0 - i * 7);
            graphics.fill(
                    centerX - size / 2 + ring,
                    top + ring,
                    centerX + size / 2 - ring,
                    top + ring + 1,
                    (alpha << 24) | (palette & 0x00FFFFFF)
            );
        }

        // Stable WorldGate-style emblem silhouette.
        int emblemTop = top + size / 4;
        int emblemLeft = centerX - size / 6;
        int emblemRight = centerX + size / 6;
        graphics.fill(emblemLeft, emblemTop, emblemRight, emblemTop + size / 2, 0xFFEDE7FF);
        graphics.fill(emblemLeft - 2, emblemTop + 3, emblemLeft + 2, emblemTop + size / 2 - 3, palette);
        graphics.fill(emblemRight - 2, emblemTop + 3, emblemRight + 2, emblemTop + size / 2 - 3, palette);

        String label = "ELITE";
        graphics.centeredText(font, label, centerX, top + size - 19, 0xFFFFFFFF);
        graphics.centeredText(font, Integer.toString(safeLevel), centerX, top + size / 2 - 4, 0xFF2A1648);
    }
}
