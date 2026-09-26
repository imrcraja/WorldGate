package com.rcraja.worldgate.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Placeholder notifications panel opened from the bell icon on the title
 * screen. Currently shows a static "all caught up" state - wire this up to
 * a real notifications feed/backend later.
 */
public final class NotificationsScreen extends Screen {
    private final Screen parent;

    public NotificationsScreen(Screen parent) {
        super(Component.literal("Notifications"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(width / 2 - 50, height - 30, 100, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        int cx = width / 2;
        g.centeredText(font, Component.literal("NOTIFICATIONS"), cx, 30, 0xFFFFFFFF);
        g.centeredText(font, Component.literal("You're all caught up."), cx, 50, 0xFF9AA7B4);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
