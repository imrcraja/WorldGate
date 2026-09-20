package com.rcraja.worldgate.client.gui;

import com.rcraja.worldgate.Constants;
import com.rcraja.worldgate.client.WorldGateModClient;
import com.rcraja.worldgate.network.HostBridge;
import com.rcraja.worldgate.network.RelayBridge;

import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.GameType;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
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
            String hostAddress = HostBridge.getAdvertiseAddress();

            String code = WorldGateModClient.ROOM_MANAGER.createRoom(
                    hostAddress,
                    port
            );

            if (code != null) {
                boolean relayStarted = HostBridge.startRelay(code);

                WorldGateModClient.CURRENT_ROOM_CODE = code;
                WorldGateModClient.startHeartbeat(true);

                this.minecraft.execute(() -> {
                    if (!relayStarted) {
                        this.minecraft.player.sendSystemMessage(
                                Component.literal(
                                        "WorldGate: relay could not start; LAN fallback is available."
                                )
                        );
                    }

                    this.minecraft.player.sendSystemMessage(
                            Component.translatable(
                                    "worldgate.msg.room_code",
                                    code
                            )
                    );

                    startRoomListeners(code);
                });

            } else {
                HostBridge.stop();

                this.minecraft.execute(() ->
                        this.minecraft.player.sendSystemMessage(
                                Component.translatable(
                                        "worldgate.msg.create_failed"
                                )
                        )
                );
            }
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

                String hostAddress = extractJsonString(roomJson, "hostAddress");
                int hostPort = extractJsonInt(roomJson, "hostPort");

                if (hostAddress == null || hostAddress.isBlank()
                        || hostPort <= 0 || hostPort > 65535) {
                    this.minecraft.player.sendSystemMessage(
                            Component.literal("WorldGate: invalid host address."));
                    return;
                }

                String ign = this.minecraft.player.getName().getString();
                WorldGateModClient.ROOM_MANAGER.playerJoin(code, ign);
                WorldGateModClient.startHeartbeat(false);

                int relayPort = RelayBridge.startPlayer(code);

                ServerAddress address;
                boolean usingRelay = relayPort > 0;

                if (usingRelay) {
                    address = new ServerAddress(
                            "127.0.0.1",
                            relayPort
                    );
                } else {
                    address = new ServerAddress(
                            hostAddress,
                            hostPort
                    );
                }

                ServerData serverData = new ServerData(
                        "WorldGate " + code,
                        address.toString(),
                        ServerData.Type.OTHER
                );

                String connectionType = usingRelay
                        ? "Internet relay"
                        : "LAN fallback";

                this.minecraft.player.sendSystemMessage(
                        Component.literal(
                                "WorldGate: connecting via "
                                        + connectionType
                                        + " to "
                                        + address.getHost()
                                        + ":"
                                        + address.getPort()
                        )
                );

                ConnectScreen.startConnecting(
                        this,
                        this.minecraft,
                        address,
                        serverData,
                        false,
                        null
                );
            });
        });
    }

    private static String extractJsonString(String json, String key) {
        if (json == null || key == null) return null;

        String marker = "\"" + key + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) return null;

        start += marker.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;

        return json.substring(start, end);
    }

    private static int extractJsonInt(String json, String key) {
        if (json == null || key == null) return -1;

        String marker = "\"" + key + "\":";
        int start = json.indexOf(marker);
        if (start < 0) return -1;

        start += marker.length();

        while (start < json.length()
                && Character.isWhitespace(json.charAt(start))) {
            start++;
        }

        int end = start;
        while (end < json.length()
                && Character.isDigit(json.charAt(end))) {
            end++;
        }

        if (end == start) return -1;

        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return -1;
        }
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
