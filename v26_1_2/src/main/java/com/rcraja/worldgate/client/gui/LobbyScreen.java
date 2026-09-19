package com.rcraja.worldgate.client.gui;

import com.rcraja.worldgate.client.WorldGateModClient;
import com.rcraja.worldgate.network.EmoteManager;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Character/lobby screen -- skin URL field and emote buttons.
 * Setting the skin is a placeholder (needs a rendering-layer mixin, TODO);
 * the emote buttons are fully wired to EmoteManager and will broadcast to
 * everyone in the current room right now. Minecraft 26.1.2 build.
 */
public class LobbyScreen extends Screen {
    private final Screen parent;
    private EditBox skinUrlBox;

    public LobbyScreen(Screen parent) {
        super(Component.translatable("worldgate.lobby.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 90;

        this.skinUrlBox = new EditBox(this.font, centerX - 100, y, 200, 20,
                Component.translatable("worldgate.lobby.skin_hint"));
        this.skinUrlBox.setMaxLength(256);
        this.skinUrlBox.setHint(Component.translatable("worldgate.lobby.skin_hint"));
        this.addRenderableWidget(this.skinUrlBox);

        this.addRenderableWidget(Button.builder(Component.translatable("worldgate.lobby.set_skin"), btn ->
                this.minecraft.player.sendSystemMessage(
                        Component.translatable("worldgate.lobby.skin_todo"))
        ).bounds(centerX - 100, y + 25, 200, 20).build());

        int emoteY = y + 55;
        int i = 0;
        for (String emote : EmoteManager.DEFAULT_EMOTES) {
            int col = i % 2;
            int row = i / 2;
            this.addRenderableWidget(Button.builder(Component.literal(capitalize(emote)), btn -> sendEmote(emote))
                    .bounds(centerX - 100 + col * 105, emoteY + row * 25, 97, 20).build());
            i++;
        }

        this.addRenderableWidget(Button.builder(Component.translatable("worldgate.button.back"), btn ->
                this.minecraft.setScreen(parent)
        ).bounds(centerX - 100, emoteY + ((i / 2) + 1) * 25 + 10, 200, 20).build());
    }

    private void sendEmote(String emote) {
        String room = WorldGateModClient.CURRENT_ROOM_CODE;
        if (room == null) {
            this.minecraft.player.sendSystemMessage(
                    Component.translatable("worldgate.lobby.no_room"));
            return;
        }
        WorldGateModClient.EXECUTOR.submit(() -> WorldGateModClient.EMOTE_MANAGER.sendEmote(room, emote));
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
