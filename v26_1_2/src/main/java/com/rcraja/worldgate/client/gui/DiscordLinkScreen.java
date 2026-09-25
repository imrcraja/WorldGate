package com.rcraja.worldgate.client.gui;

import com.rcraja.worldgate.network.DiscordLinkManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class DiscordLinkScreen extends Screen {
    private final Screen parent;
    private String status = Component.translatable("worldgate.discord.linking").getString();

    public DiscordLinkScreen(Screen parent) {
        super(Component.translatable("worldgate.settings.discord"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(
                Component.translatable(DiscordLinkManager.isLinked() ? "worldgate.discord.linked" : "worldgate.discord.link"),
                b -> {
                    if (DiscordLinkManager.isLinked()) {
                        status = Component.translatable("worldgate.discord.linked_as", DiscordLinkManager.linkedName()).getString();
                        return;
                    }
                    status = Component.translatable("worldgate.discord.opening").getString();
                    DiscordLinkManager.begin(() -> status = DiscordLinkManager.isLinked()
                            ? "Linked as " + DiscordLinkManager.linkedName()
                            : "Authorization was not completed.");
                }).bounds(width / 2 - 120, 86, 240, 22).build());

        addRenderableWidget(Button.builder(Component.translatable("worldgate.button.back"),
                b -> onClose()).bounds(width / 2 - 120, height - 34, 240, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        g.centeredText(font, Component.translatable("worldgate.settings.discord"), width / 2, 24, 0xFFFFFFFF);
        g.centeredText(font, Component.translatable("worldgate.discord.subtitle"), width / 2, 46, 0xFF9AA7B4);
        g.centeredText(font, DiscordLinkManager.isLinked()
                ? "Account linked • " + DiscordLinkManager.linkedName()
                : "Not linked", width / 2, 66, 0xFFB8C2CC);
        g.centeredText(font, status, width / 2, height - 56, 0xFF7DE2FF);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
