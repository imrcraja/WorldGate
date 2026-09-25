package com.rcraja.worldgate.client.gui;

import com.rcraja.worldgate.network.DiscordLinkManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class DiscordLinkScreen extends Screen {
    private final Screen parent;
    private String status = "Discord account linking";

    public DiscordLinkScreen(Screen parent) {
        super(Component.literal("Discord"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(
                Component.literal(DiscordLinkManager.isLinked() ? "Linked" : "Link Discord"),
                b -> {
                    if (DiscordLinkManager.isLinked()) {
                        status = "Linked as " + DiscordLinkManager.linkedName();
                        return;
                    }
                    status = "Opening Discord authorization...";
                    DiscordLinkManager.begin(() -> status = DiscordLinkManager.isLinked()
                            ? "Linked as " + DiscordLinkManager.linkedName()
                            : "Authorization was not completed.");
                }).bounds(width / 2 - 120, 86, 240, 22).build());

        addRenderableWidget(Button.builder(Component.literal("Back"),
                b -> onClose()).bounds(width / 2 - 120, height - 34, 240, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        g.centeredText(font, "DISCORD", width / 2, 24, 0xFFFFFFFF);
        g.centeredText(font, "Authorize your WorldGate account with Discord.", width / 2, 46, 0xFF9AA7B4);
        g.centeredText(font, DiscordLinkManager.isLinked()
                ? "Account linked • " + DiscordLinkManager.linkedName()
                : "Not linked", width / 2, 66, 0xFFB8C2CC);
        g.centeredText(font, status, width / 2, height - 56, 0xFF7DE2FF);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
