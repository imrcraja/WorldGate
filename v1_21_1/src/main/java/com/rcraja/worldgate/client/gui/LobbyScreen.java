package com.rcraja.worldgate.client.gui;

import com.rcraja.worldgate.client.WorldGateModClient;
import com.rcraja.worldgate.network.EmoteManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Character/lobby screen -- skin URL field and emote buttons (see the
 * 26.1.2 module's LobbyScreen for design notes). Minecraft 1.21.1 build.
 */
public class LobbyScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget skinUrlBox;

    public LobbyScreen(Screen parent) {
        super(Text.translatable("worldgate.lobby.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 90;

        this.skinUrlBox = new TextFieldWidget(this.textRenderer, centerX - 100, y, 200, 20,
                Text.translatable("worldgate.lobby.skin_hint"));
        this.skinUrlBox.setMaxLength(256);
        this.skinUrlBox.setPlaceholder(Text.translatable("worldgate.lobby.skin_hint"));
        this.addDrawableChild(this.skinUrlBox);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("worldgate.lobby.set_skin"), btn ->
                this.client.player.sendMessage(Text.translatable("worldgate.lobby.skin_todo"), false)
        ).dimensions(centerX - 100, y + 25, 200, 20).build());

        int emoteY = y + 55;
        int i = 0;
        for (String emote : EmoteManager.DEFAULT_EMOTES) {
            int col = i % 2;
            int row = i / 2;
            this.addDrawableChild(ButtonWidget.builder(Text.literal(capitalize(emote)), btn -> sendEmote(emote))
                    .dimensions(centerX - 100 + col * 105, emoteY + row * 25, 97, 20).build());
            i++;
        }

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("worldgate.button.back"), btn ->
                this.client.setScreen(parent)
        ).dimensions(centerX - 100, emoteY + ((i / 2) + 1) * 25 + 10, 200, 20).build());
    }

    private void sendEmote(String emote) {
        String room = WorldGateModClient.CURRENT_ROOM_CODE;
        if (room == null) {
            this.client.player.sendMessage(Text.translatable("worldgate.lobby.no_room"), false);
            return;
        }
        WorldGateModClient.EXECUTOR.submit(() -> WorldGateModClient.EMOTE_MANAGER.sendEmote(room, emote));
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
