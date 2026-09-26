package com.rcraja.worldgate.client.gui;

import com.rcraja.worldgate.network.VoiceManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * One-time microphone permission notice.
 *
 * This is intentionally informational only: WorldGate does not expose a
 * persistent microphone on/off setting here and does not repeatedly interrupt
 * the player after the notice has been dismissed.
 */
public final class VoicePermissionScreen extends Screen {
    private final Screen parent;
    private final VoiceManager voice;

    public VoicePermissionScreen(Screen parent, VoiceManager voice) {
        super(Component.translatable("worldgate.voice.permission.title"));
        this.parent = parent;
        this.voice = voice;
    }

    @Override
    protected void init() {
        int w = Math.min(440, width - 40);
        int x = (width - w) / 2;

        addRenderableWidget(Button.builder(
                Component.translatable("worldgate.voice.permission.close"),
                b -> {
                    WorldGateModClientPermissionNotice.markShown();
                    onClose();
                }
        ).bounds(x, height / 2 + 18, w, 24).build());
    }

    @Override
    public void onClose() {
        WorldGateModClientPermissionNotice.markShown();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor g,
            int mx,
            int my,
            float delta
    ) {
        super.extractRenderState(g, mx, my, delta);

        g.centeredText(
                font,
                Component.translatable("worldgate.voice.permission.title"),
                width / 2,
                height / 2 - 72,
                0xFFFFFFFF
        );
        g.centeredText(
                font,
                Component.translatable("worldgate.voice.permission.body"),
                width / 2,
                height / 2 - 42,
                0xFFB9C8D8
        );
        g.centeredText(
                font,
                Component.translatable("worldgate.voice.permission.body2"),
                width / 2,
                height / 2 - 22,
                0xFFB9C8D8
        );
    }

    /**
     * Kept local to the permission screen so the notice is persisted even if
     * the player closes it with Escape instead of the button.
     */
    private static final class WorldGateModClientPermissionNotice {
        private static final String KEY = "voicePermissionNoticeShown";

        private static boolean hasBeenShown() {
            return "true".equals(
                    com.rcraja.worldgate.client.LocalWorldGateData.get(KEY, "false")
            );
        }

        private static void markShown() {
            com.rcraja.worldgate.client.LocalWorldGateData.set(KEY, "true");
        }
    }

    public static boolean shouldShow() {
        return !WorldGateModClientPermissionNotice.hasBeenShown();
    }
}
