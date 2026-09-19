package com.rcraja.worldgate.client.gui;

import com.rcraja.worldgate.Constants;
import com.rcraja.worldgate.client.WorldGateModClient;
import com.rcraja.worldgate.network.HostBridge;

import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.GameType;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Main WorldGate menu, opened from the Escape (Pause) screen.
 * Minecraft 26.1.2 (Mojang mappings) build.
 */
public class WorldGateScreen extends Screen {
    private final Screen parent;
    private EditBox roomCodeBox;

    public WorldGateScreen(Screen parent) {
        super(Component.translatable("worldgate.screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 95;

        this.roomCodeBox = new EditBox(this.font, centerX - 100, y, 140, 20,
                Component.translatable("worldgate.roomcode.hint"));
        this.roomCodeBox.setMaxLength(6);
        this.roomCodeBox.setHint(Component.translatable("worldgate.roomcode.hint"));
        this.addRenderableWidget(this.roomCodeBox);

        this.addRenderableWidget(Button.builder(Component.translatable("worldgate.button.join"), btn -> onJoin())
                .bounds(centerX + 45, y, 55, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("worldgate.button.create"), btn -> onCreate())
                .bounds(centerX - 100, y + 25, 200, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("worldgate.button.friends"), btn ->
                this.minecraft.setScreen(new FriendsScreen(this))
        ).bounds(centerX - 100, y + 50, 200, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("worldgate.button.lobby"), btn ->
                this.minecraft.setScreen(new LobbyScreen(this))
        ).bounds(centerX - 100, y + 75, 200, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("worldgate.button.report"), btn ->
                openLink(Constants.GITHUB_ISSUES)
        ).bounds(centerX - 100, y + 110, 97, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("worldgate.button.youtube"), btn ->
                openLink(Constants.YOUTUBE_CHANNEL)
        ).bounds(centerX + 3, y + 110, 97, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("worldgate.button.back"), btn ->
                this.minecraft.setScreen(parent)
        ).bounds(centerX - 100, y + 140, 200, 20).build());
    }

    private void onCreate() {
        this.minecraft.player.sendSystemMessage(
                Component.translatable("worldgate.msg.creating"));

        IntegratedServer server = this.minecraft.getSingleplayerServer();

        if (server == null) {
            this.minecraft.player.sendSystemMessage(
                    Component.literal("WorldGate: no singleplayer world is running."));
            return;
        }

        if (!server.isPublished()) {
            boolean published = server.publishServer(
                    GameType.DEFAULT_MODE,
                    true,
                    0
            );

            if (!published) {
                this.minecraft.player.sendSystemMessage(
                        Component.literal("WorldGate: failed to publish the world."));
                return;
            }
        }

        int port = server.getPort();

        if (!HostBridge.start(port)) {
            this.minecraft.player.sendSystemMessage(
                    Component.literal("WorldGate: failed to start host bridge."));
            return;
        }

        this.minecraft.player.sendSystemMessage(
                Component.literal("WorldGate: Minecraft server published on port " + port));

        WorldGateModClient.EXECUTOR.submit(() -> {
            String code = WorldGateModClient.ROOM_MANAGER.createRoom("0.0.0.0", port);

            this.minecraft.execute(() -> {
                if (code == null) {
                    HostBridge.stop();
                    this.minecraft.player.sendSystemMessage(
                            Component.translatable("worldgate.msg.create_failed"));
                    return;
                }

                WorldGateModClient.CURRENT_ROOM_CODE = code;

                this.minecraft.player.sendSystemMessage(
                        Component.translatable("worldgate.msg.room_code", code));

                startRoomListeners(code);
            });
        });
    }

    private void onJoin() {
        String code = this.roomCodeBox.getValue().trim().toUpperCase();
        if (code.isEmpty()) return;
        this.minecraft.player.sendSystemMessage(
                Component.translatable("worldgate.msg.joining"));
        WorldGateModClient.EXECUTOR.submit(() -> {
            String roomJson = WorldGateModClient.ROOM_MANAGER.getRoom(code);
            this.minecraft.execute(() -> {
                if (roomJson == null || roomJson.equals("null")) {
                    this.minecraft.player.sendSystemMessage(
                            Component.translatable("worldgate.msg.room_not_found"));
                    return;
                }
                WorldGateModClient.CURRENT_ROOM_CODE = code;
                // TODO: actually connect to hostAddress/hostPort from roomJson
                // (real player-to-player networking is the next phase).
                this.minecraft.player.sendSystemMessage(
                        Component.literal(roomJson));
                startRoomListeners(code);
            });
        });
    }

    private void startRoomListeners(String code) {
        WorldGateModClient.CHAT_MANAGER.listen(code, (uid, text) ->
                this.minecraft.execute(() ->
                        this.minecraft.player.sendSystemMessage(
                                Component.literal("<" + shortUid(uid) + "> " + text))));

        WorldGateModClient.EMOTE_MANAGER.listen(code, (uid, emote) ->
                this.minecraft.execute(() ->
                        this.minecraft.player.sendSystemMessage(
                                Component.translatable("worldgate.msg.emote", shortUid(uid), emote))));
    }

    private static String shortUid(String uid) {
        return uid == null ? "?" : uid.substring(0, Math.min(6, uid.length()));
    }

    private void openLink(String url) {
        // NOTE: ConfirmLinkScreen's constructor signature has shifted between
        // Minecraft versions before. If this line fails to compile, check the
        // decompiled ConfirmLinkScreen class (via the loom genSources task)
        // and adjust the arguments to match.
        this.minecraft.setScreen(new ConfirmLinkScreen(confirmed -> {
            if (confirmed) {
                this.minecraft.setScreen(this);
            }
            this.minecraft.setScreen(this);
        }, url, true));
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
