package com.rcraja.worldgate.client.gui;

import com.rcraja.worldgate.Constants;
import com.rcraja.worldgate.client.WorldGateModClient;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/**
 * Main WorldGate menu, opened from the Escape (pause) menu.
 * Minecraft 1.21.1 (Yarn mappings) build.
 */
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

        this.roomCodeBox = new TextFieldWidget(this.textRenderer, centerX - 100, y, 140, 20,
                Text.translatable("worldgate.roomcode.hint"));
        this.roomCodeBox.setMaxLength(6);
        this.roomCodeBox.setPlaceholder(Text.translatable("worldgate.roomcode.hint"));
        this.addDrawableChild(this.roomCodeBox);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("worldgate.button.join"), btn -> onJoin())
                .dimensions(centerX + 45, y, 55, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("worldgate.button.create"), btn -> onCreate())
                .dimensions(centerX - 100, y + 25, 200, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("worldgate.button.friends"), btn ->
                this.client.setScreen(new FriendsScreen(this))
        ).dimensions(centerX - 100, y + 50, 200, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("worldgate.button.lobby"), btn ->
                this.client.setScreen(new LobbyScreen(this))
        ).dimensions(centerX - 100, y + 75, 200, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("worldgate.button.report"), btn ->
                openLink(Constants.GITHUB_ISSUES)
        ).dimensions(centerX - 100, y + 110, 97, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("worldgate.button.youtube"), btn ->
                openLink(Constants.YOUTUBE_CHANNEL)
        ).dimensions(centerX + 3, y + 110, 97, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("worldgate.button.back"), btn ->
                this.client.setScreen(parent)
        ).dimensions(centerX - 100, y + 140, 200, 20).build());
    }

    private void onCreate() {
        this.client.player.sendMessage(Text.translatable("worldgate.msg.creating"), false);
        WorldGateModClient.EXECUTOR.submit(() -> {
            String code = WorldGateModClient.ROOM_MANAGER.createRoom("0.0.0.0", 25565);
            this.client.execute(() -> {
                if (code == null) {
                    this.client.player.sendMessage(Text.translatable("worldgate.msg.create_failed"), false);
                    return;
                }
                WorldGateModClient.CURRENT_ROOM_CODE = code;
                this.client.player.sendMessage(Text.translatable("worldgate.msg.room_code", code), false);
                startRoomListeners(code);
            });
        });
    }

    private void onJoin() {
        String code = this.roomCodeBox.getText().trim().toUpperCase();
        if (code.isEmpty()) return;
        this.client.player.sendMessage(Text.translatable("worldgate.msg.joining"), false);
        WorldGateModClient.EXECUTOR.submit(() -> {
            String roomJson = WorldGateModClient.ROOM_MANAGER.getRoom(code);
            this.client.execute(() -> {
                if (roomJson == null || roomJson.equals("null")) {
                    this.client.player.sendMessage(Text.translatable("worldgate.msg.room_not_found"), false);
                    return;
                }
                WorldGateModClient.CURRENT_ROOM_CODE = code;
                // TODO: actually connect to hostAddress/hostPort from roomJson
                // (real player-to-player networking is the next phase).
                this.client.player.sendMessage(Text.literal(roomJson), false);
                startRoomListeners(code);
            });
        });
    }

    private void startRoomListeners(String code) {
        WorldGateModClient.CHAT_MANAGER.listen(code, (uid, text) ->
                this.client.execute(() ->
                        this.client.player.sendMessage(Text.literal("<" + shortUid(uid) + "> " + text), false)));

        WorldGateModClient.EMOTE_MANAGER.listen(code, (uid, emote) ->
                this.client.execute(() ->
                        this.client.player.sendMessage(
                                Text.translatable("worldgate.msg.emote", shortUid(uid), emote), false)));
    }

    private static String shortUid(String uid) {
        return uid == null ? "?" : uid.substring(0, Math.min(6, uid.length()));
    }

    private void openLink(String url) {
        this.client.setScreen(new ConfirmLinkScreen(confirmed -> {
            if (confirmed) {
                Util.getOperatingSystem().open(url);
            }
            this.client.setScreen(this);
        }, url, true));
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
