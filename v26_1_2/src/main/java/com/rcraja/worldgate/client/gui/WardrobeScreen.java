package com.rcraja.worldgate.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WorldGate wardrobe hub. The screen owns lightweight local selection state so
 * the UI is immediately interactive while server-backed ownership/catalog data
 * can be wired in later without changing navigation.
 */
public final class WardrobeScreen extends Screen {
    public enum Tab { COSMETICS, EMOTES }

    private static final Map<String, Boolean> OWNED = new LinkedHashMap<>();
    private static String equippedCosmetic = "WorldGate";
    private static String equippedEmote = "Wave";

    private final Screen parent;
    private final Tab tab;
    private String status = "";

    public WardrobeScreen(Screen parent, Tab tab) {
        super(Component.literal(tab == Tab.EMOTES ? "Emotes" : "Cosmetics"));
        this.parent = parent;
        this.tab = tab;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Cosmetics"),
                b -> open(Tab.COSMETICS))
                .bounds(width / 2 - 156, 58, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Emotes"),
                b -> open(Tab.EMOTES))
                .bounds(width / 2 - 52, 58, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Elite Store"),
                b -> minecraft.setScreen(new EliteCoinScreen(this)))
                .bounds(width / 2 + 52, 58, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Back"),
                b -> onClose())
                .bounds(width / 2 - 100, height - 30, 200, 20).build());

        int left = Math.max(24, width / 2 - 310);
        int top = 92;
        int gap = 10;
        int cardW = 148;
        int cardH = 92;

        for (int i = 0; i < 8; i++) {
            final int index = i;
            int col = i % 4;
            int row = i / 4;
            int x = left + col * (cardW + gap);
            int y = top + row * (cardH + gap);

            addRenderableWidget(Button.builder(
                    Component.literal(i == 0 ? "Equipped" : "Select"),
                    b -> select(index))
                    .bounds(x + 14, y + 66, cardW - 28, 20)
                    .build());
        }
    }

    private void open(Tab next) {
        if (next != tab && minecraft != null) {
            minecraft.setScreen(new WardrobeScreen(parent, next));
        }
    }

    private void select(int index) {
        if (index != 0) {
            status = "This WorldGate slot is ready for future catalog content.";
            return;
        }

        if (tab == Tab.EMOTES) {
            equippedEmote = "Wave";
            OWNED.put("emote:wave", true);
            status = "Equipped emote: Wave";
        } else {
            equippedCosmetic = "WorldGate";
            OWNED.put("cosmetic:worldgate", true);
            status = "Equipped cosmetic: WorldGate";
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);

        String title = tab == Tab.EMOTES ? "EMOTES" : "COSMETICS";
        String subtitle = tab == Tab.EMOTES
                ? "Choose an emote to use in WorldGate."
                : "Choose your active WorldGate cosmetic.";

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

            boolean active = i == 0;
            boolean equipped = active;
            g.fill(x, y, x + cardW, y + cardH, active ? 0xCC15212A : 0xCC10161D);
            g.outline(x, y, cardW, cardH, active ? 0xFF4B90A8 : 0xFF2B3742);

            String name;
            String detail;
            if (active && tab == Tab.EMOTES) {
                name = "Wave";
                detail = equippedEmote.equals("Wave") ? "Equipped" : "Owned";
            } else if (active) {
                name = "WorldGate";
                detail = equippedCosmetic.equals("WorldGate") ? "Equipped" : "Owned";
            } else {
                name = "Coming Soon";
                detail = "New content";
            }

            g.centeredText(font, Component.literal(name),
                    x + cardW / 2, y + 24,
                    active ? 0xFF7DE2FF : 0xFF7D8792);
            g.centeredText(font, Component.literal(detail),
                    x + cardW / 2, y + 44, 0xFF8E9AA6);
        }

        if (status.isBlank()) {
            status = tab == Tab.EMOTES
                    ? "Equipped: " + equippedEmote
                    : "Equipped: " + equippedCosmetic;
        }
        g.centeredText(font, Component.literal(status),
                width / 2, height - 52, 0xFF8E9AA6);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
