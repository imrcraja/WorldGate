package com.rcraja.worldgate.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rcraja.worldgate.Constants;
import com.rcraja.worldgate.client.WorldGateModClient;

import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

public class WorldGateScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget roomCodeBox;

    public WorldGateScreen(Screen parent) {
        super(Text.translatable("worldgate.screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 95;

        roomCodeBox = new TextFieldWidget(
                this.textRenderer, centerX - 100, y, 140, 20,
                Text.translatable("worldgate.roomcode.hint"));
        roomCodeBox.setMaxLength(6);
        roomCodeBox.setPlaceholder(Text.translatable("worldgate.roomcode.hint"));
        addDrawableChild(roomCodeBox);

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("worldgate.button.join"), btn -> onJoin())
                .dimensions(centerX + 45, y, 55, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("worldgate.button.create"), btn -> onCreate())
                .dimensions(centerX - 100, y + 25, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("worldgate.button.friends"), btn ->
                        this.client.setScreen(new FriendsScreen(this)))
                .dimensions(centerX - 100, y + 50, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("worldgate.button.lobby"), btn ->
                        this.client.setScreen(new LobbyScreen(this)))
                .dimensions(centerX - 100, y + 75, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("worldgate.button.report"), btn ->
                        openLink(Constants.GITHUB_ISSUES))
                .dimensions(centerX - 100, y + 110, 97, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("worldgate.button.youtube"), btn ->
                        openLink(Constants.YOUTUBE_CHANNEL))
                .dimensions(centerX + 3, y + 110, 97, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("worldgate.button.back"), btn -> close())
                .dimensions(centerX - 100, y + 140, 200, 20).build());
    }

    private void onCreate() {
        this.client.player.sendMessage(
                Text.translatable("worldgate.msg.creating"), false);

        WorldGateModClient.EXECUTOR.submit(() -> {
            String code = WorldGateModClient.ROOM_MANAGER.createRoom("0.0.0.0", 25565);

            this.client.execute(() -> {
                if (code == null) {
                    this.client.player.sendMessage(
                            Text.translatable("worldgate.msg.create_failed"), false);
                    return;
                }

                WorldGateModClient.CURRENT_ROOM_CODE = code;
                this.client.player.sendMessage(
                        Text.translatable("worldgate.msg.room_code", code), false);
                startRoomListeners(code);
            });
        });
    }

    private void onJoin() {
        String code = roomCodeBox.getText().trim().toUpperCase();
        if (code.isEmpty()) return;

        this.client.player.sendMessage(
                Text.translatable("worldgate.msg.joining"), false);

        WorldGateModClient.EXECUTOR.submit(() -> {
            String roomJson = WorldGateModClient.ROOM_MANAGER.getRoom(code);

            this.client.execute(() -> {
                if (roomJson == null || roomJson.equals("null")) {
                    this.client.player.sendMessage(
                            Text.translatable("worldgate.msg.room_not_found"), false);
                    return;
                }

                WorldGateModClient.CURRENT_ROOM_CODE = code;
                startRoomListeners(code);
            });
        });
    }

    private void startRoomListeners(String code) {
        WorldGateModClient.CHAT_MANAGER.listen(code, (uid, text) ->
                this.client.execute(() ->
                        this.client.player.sendMessage(
                                Text.literal("<" + displayName(uid) + "> " + text), false)));

        WorldGateModClient.EMOTE_MANAGER.listen(code, (uid, emote) ->
                this.client.execute(() ->
                        this.client.player.sendMessage(
                                Text.translatable(
                                        "worldgate.msg.emote",
                                        displayName(uid), emote), false)));
    }

    private String displayName(String uid) {
        if (uid == null || uid.isBlank()) return "Player";

        String myUid = WorldGateModClient.FRIEND_MANAGER.myUid();
        if (uid.equals(myUid) && this.client.player != null) {
            return this.client.player.getName().getString();
        }

        return "Player";
    }

    private void openLink(String url) {
        this.client.setScreen(new ConfirmLinkScreen(confirmed -> {
            if (confirmed) Util.getOperatingSystem().open(url);
            this.client.setScreen(this);
        }, url, true));
    }

    @Override
    public void close() {
        WorldGateModClient.CHAT_MANAGER.stopListening();
        WorldGateModClient.EMOTE_MANAGER.stopListening();
        this.client.setScreen(parent);
    }
}
