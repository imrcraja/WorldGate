package com.rcraja.worldgate.client.gui;

import com.rcraja.worldgate.client.WorldGateModClient;
import com.rcraja.worldgate.network.RelayBridge;
import com.rcraja.worldgate.client.gui.WorldGatePreferences;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.language.I18n;

public final class SettingsScreen extends Screen {
    private final Screen parent;
    private String mode;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("worldgate.settings.title"));
        this.parent = parent;
        this.mode = WorldGateModClient.getNetworkMode();
    }

    @Override
    protected void init() {
        int x = width / 2 - 120;
        addRenderableWidget(Button.builder(Component.translatable("worldgate.settings.connection", label(mode)),
                b -> {
                    mode = switch (mode) {
                        case "lan" -> "internet";
                        case "internet" -> "auto";
                        default -> "lan";
                    };
                    WorldGateModClient.setNetworkMode(mode);
                    b.setMessage(Component.translatable("worldgate.settings.connection", label(mode)));
                }).bounds(x, 76, 240, 20).build());

        addRenderableWidget(Button.builder(Component.literal(
                "WorldGate overlay: " + (WorldGatePreferences.overlayEnabled() ? "ON" : "OFF")),
                b -> {
                    boolean enabled = !WorldGatePreferences.overlayEnabled();
                    WorldGatePreferences.setOverlayEnabled(enabled);
                    b.setMessage(Component.literal("WorldGate overlay: " + (enabled ? "ON" : "OFF")));
                }).bounds(x, 104, 240, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("worldgate.settings.test_relay"),
                b -> sendMessage(RelayBridge.isConnected()
                        ? I18n.get("worldgate.settings.relay_connected")
                        : I18n.get("worldgate.settings.relay_idle")))
                .bounds(x, 104, 240, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("worldgate.settings.discord"),
                b -> minecraft.setScreen(new DiscordLinkScreen(this)))
                .bounds(x, 132, 240, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("worldgate.settings.security"),
                b -> minecraft.setScreen(new AccountSecurityScreen(this)))
                .bounds(x, 160, 240, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("worldgate.settings.privacy"),
                b -> minecraft.setScreen(new LocalDataScreen(this)))
                .bounds(x, 188, 240, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("worldgate.button.back"),
                b -> onClose()).bounds(x, height - 30, 240, 20).build());
    }

    private static String label(String mode) {
        return switch (mode) {
            case "lan" -> "LAN only";
            case "internet" -> "Internet only";
            default -> "Auto";
        };
    }

    private void sendMessage(String message) {
        if (minecraft != null && minecraft.player != null)
            minecraft.player.sendSystemMessage(Component.literal(message));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        g.centeredText(font, I18n.get("worldgate.settings.heading"), width / 2, 22, 0xFFFFFFFF);
        g.centeredText(font, I18n.get("worldgate.settings.subtitle"), width / 2, 40, 0xFF9AA7B4);
        g.centeredText(font, I18n.get("worldgate.settings.auto_note"), width / 2, 58, 0xFF7D8792);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    private static final class LocalDataScreen extends Screen {
        private final Screen parent;
        LocalDataScreen(Screen parent) { super(Component.translatable("worldgate.settings.privacy")); this.parent = parent; }

        @Override protected void init() {
            addRenderableWidget(Button.builder(Component.translatable("worldgate.button.back"), b -> onClose())
                    .bounds(width / 2 - 100, height - 30, 200, 20).build());
        }

        @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            super.extractRenderState(g, mx, my, delta);
            g.centeredText(font, I18n.get("worldgate.settings.local_heading"), width / 2, 24, 0xFFFFFFFF);
            g.centeredText(font, I18n.get("worldgate.settings.local_client"), width / 2, 58, 0xFF7DE2FF);
            g.centeredText(font, I18n.get("worldgate.settings.local_profiles"), width / 2, 78, 0xFFB8C2CC);
            g.centeredText(font, I18n.get("worldgate.settings.local_world"), width / 2, 106, 0xFFFFD166);
            g.centeredText(font, I18n.get("worldgate.settings.local_network"), width / 2, 126, 0xFF8E9AA6);
        }

        @Override public void onClose() { minecraft.setScreen(parent); }
    }
}
