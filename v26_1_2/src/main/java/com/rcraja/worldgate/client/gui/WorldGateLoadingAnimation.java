package com.rcraja.worldgate.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;

public final class WorldGateLoadingAnimation {
    private WorldGateLoadingAnimation() {}

    public static void draw(GuiGraphicsExtractor g, Font font, int x, int y) {
        long now = System.currentTimeMillis() / 180L;
        int active = (int)(now % 4);
        StringBuilder dots = new StringBuilder("Loading");
        for (int i = 0; i < 3; i++) dots.append(i == active ? '●' : '·');
        g.centeredText(font, dots.toString(), x, y, 0xFF7DE2FF);
    }
}
