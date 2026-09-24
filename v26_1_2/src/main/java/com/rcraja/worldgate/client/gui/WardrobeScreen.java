package com.rcraja.worldgate.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * WorldGate's dedicated cosmetics/emotes hub.
 * The store remains separate so this screen can later host equipped/owned items
 * without turning the main menu into a nested options list.
 */
public final class WardrobeScreen extends Screen {
    public enum Tab { COSMETICS, EMOTES }

    private final Screen parent;
    private Tab tab;

    public WardrobeScreen(Screen parent, Tab tab) {
        super(Component.literal(tab == Tab.EMOTES ? "Emotes" : "Cosmetics"));
        this.parent = parent;
        this.tab = tab;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Cosmetics"), b -> switchTab(Tab.COSMETICS))
                .bounds(width / 2 - 156, 58, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Emotes"), b -> switchTab(Tab.EMOTES))
                .bounds(width / 2 - 52, 58, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Elite Store"), b -> minecraft.setScreen(new EliteCoinScreen(this)))
                .bounds(width / 2 + 52, 58, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(width / 2 - 100, height - 30, 200, 20).build());
    }

    private void switchTab(Tab next) {
        if (tab == next) return;
        tab = next;
        if (minecraft != null) minecraft.setScreen(new WardrobeScreen(parent, next));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);

        String title = tab == Tab.EMOTES ? "EMOTES" : "COSMETICS";
        String subtitle = tab == Tab.EMOTES
                ? "Your emotes and future emote collection."
                : "Your equipped cosmetics and future wardrobe collection.";

        g.centeredText(font, title, width / 2, 20, 0xFFFFFFFF);
        g.centeredText(font, subtitle, width / 2, 38, 0xFF9AA7B4);

        int left = Math.max(24, width / 2 - 310);
        int top = 92;
        int gap = 10;
        int cardW = 148;
        int cardH = 92;

        for (int i = 0; i < 8; i++) {
            int col = i % 4;
            int row = i / 4;
            int x = left + col * (cardW + gap);
            int y = top + row * (cardH + gap);
            g.fill(x, y, x + cardW, y + cardH, 0xCC10161D);
            g.outline(x, y, cardW, cardH, 0xFF2B3742);
            g.centeredText(font,
                    Component.literal(i == 0 ? "WorldGate" : "Coming Soon"),
                    x + cardW / 2, y + 35,
                    i == 0 ? 0xFF7DE2FF : 0xFF7D8792);
            g.centeredText(font,
                    Component.literal(i == 0
                            ? (tab == Tab.EMOTES ? "Emote Wheel" : "Player Preview")
                            : "New content"),
                    x + cardW / 2, y + 55, 0xFF8E9AA6);
        }

        g.centeredText(font,
                Component.literal("Items can be added here without changing the main menu layout."),
                width / 2, height - 52, 0xFF707B86);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
