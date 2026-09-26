package com.rcraja.worldgate.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class PicturesScreen extends Screen {
    private final Screen parent;
    private String status = "";

    public PicturesScreen(Screen parent) {
        super(Component.literal("Pictures"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Open Screenshots Folder"), b -> openFolder())
                .bounds(width / 2 - 110, height / 2 - 10, 220, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("worldgate.button.back"), b -> onClose())
                .bounds(width / 2 - 110, height - 35, 220, 20).build());
    }

    private void openFolder() {
        try {
            Path folder = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");
            Files.createDirectories(folder);
            Util.getPlatform().openUri(folder.toUri().toString());
            status = "Opened screenshots folder.";
        } catch (Exception e) {
            status = "Could not open screenshots folder.";
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        g.centeredText(font, Component.literal("Pictures"), width / 2, height / 2 - 55, 0xFFFFFFFF);
        g.centeredText(font, Component.literal("Your Minecraft screenshots"), width / 2, height / 2 - 35, 0xFF9AA7B4);
        if (!status.isBlank()) g.centeredText(font, Component.literal(status), width / 2, height / 2 + 25, 0xFF8E9AA6);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
