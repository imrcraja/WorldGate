package com.rcraja.worldgate.client.gui;

import com.rcraja.worldgate.client.WorldGateModClient;
import com.rcraja.worldgate.network.RelayBridge;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SettingsScreen extends Screen {
    private final Screen parent;
    private String mode;

    public SettingsScreen(Screen parent) {
        super(Component.literal("WorldGate Settings"));
        this.parent = parent;
        this.mode = WorldGateModClient.getNetworkMode();
    }

    @Override
    protected void init() {
        int x = width / 2 - 120;
        addRenderableWidget(Button.builder(Component.literal("Connection: " + label(mode)),
                b -> {
                    mode = switch (mode) {
                        case "lan" -> "internet";
                        case "internet" -> "auto";
                        default -> "lan";
                    };
                    WorldGateModClient.setNetworkMode(mode);
                    b.setMessage(Component.literal("Connection: " + label(mode)));
                }).bounds(x, 76, 240, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Test Internet Relay"),
                b -> sendMessage(RelayBridge.isConnected()
                        ? "Internet relay is connected."
                        : "Relay is idle. It connects automatically when a room uses Internet mode."))
                .bounds(x, 104, 240, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Discord Account"),
                b -> minecraft.setScreen(new DiscordLinkScreen(this)))
                .bounds(x, 132, 240, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Account Security"),
                b -> minecraft.setScreen(new AccountSecurityScreen(this)))
                .bounds(x, 160, 240, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Privacy & Local Data"),
                b -> minecraft.setScreen(new LocalDataScreen(this)))
                .bounds(x, 188, 240, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Back"),
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
        g.centeredText(font, "SETTINGS", width / 2, 22, 0xFFFFFFFF);
        g.centeredText(font, "Connection and privacy controls", width / 2, 40, 0xFF9AA7B4);
        g.centeredText(font, "Auto = Internet relay when available, LAN fallback when local.", width / 2, 58, 0xFF7D8792);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    private static final class LocalDataScreen extends Screen {
        private final Screen parent;
        LocalDataScreen(Screen parent) { super(Component.literal("Privacy & Local Data")); this.parent = parent; }

        @Override protected void init() {
            addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                    .bounds(width / 2 - 100, height - 30, 200, 20).build());
        }

        @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            super.extractRenderState(g, mx, my, delta);
            g.centeredText(font, "LOCAL PLAYER DATA", width / 2, 24, 0xFFFFFFFF);
            g.centeredText(font, "Cosmetics, emotes and UI preferences are stored on this client.", width / 2, 58, 0xFF7DE2FF);
            g.centeredText(font, "Other players' personal profile data is not copied onto the host as a storage system.", width / 2, 78, 0xFFB8C2CC);
            g.centeredText(font, "Shared Minecraft world state still runs on the host's integrated server.", width / 2, 106, 0xFFFFD166);
            g.centeredText(font, "Firebase coordinates metadata; the relay carries Minecraft traffic.", width / 2, 126, 0xFF8E9AA6);
        }

        @Override public void onClose() { minecraft.setScreen(parent); }
    }
}
