package com.rcraja.worldgate.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rcraja.worldgate.Constants;
import com.rcraja.worldgate.client.WorldGateModClient;
import com.rcraja.worldgate.network.HostBridge;
import com.rcraja.worldgate.network.RelayBridge;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;

public class WorldGateScreen extends Screen {

    private final Screen parent;
    private EditBox roomCodeBox;

    private final Map<String, Boolean> knownPlayers =
            new HashMap<>();

    private boolean roomSnapshotInitialized = false;

    public WorldGateScreen(Screen parent) {
        super(Component.translatable("worldgate.screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 95;

        roomCodeBox = new EditBox(
                this.font,
                centerX - 100,
                y,
                140,
                20,
                Component.translatable("worldgate.roomcode.hint")
        );

        roomCodeBox.setMaxLength(6);
        roomCodeBox.setHint(
                Component.translatable("worldgate.roomcode.hint")
        );

        addRenderableWidget(roomCodeBox);

        addRenderableWidget(Button.builder(
                Component.translatable("worldgate.button.join"),
                btn -> onJoin()
        ).bounds(centerX + 45, y, 55, 20).build());

        Button createButton = Button.builder(
                Component.translatable("worldgate.button.create"),
                btn -> onCreate()
        ).bounds(centerX - 100, y + 25, 200, 20).build();

        createButton.active =
                this.minecraft != null
                        && this.minecraft.player != null
                        && this.minecraft.getSingleplayerServer() != null;

        addRenderableWidget(createButton);

        addRenderableWidget(Button.builder(
                Component.translatable("worldgate.button.friends"),
                btn -> this.minecraft.setScreen(new FriendsScreen(this))
        ).bounds(centerX - 100, y + 50, 200, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("worldgate.button.lobby"),
                btn -> this.minecraft.setScreen(new LobbyScreen(this))
        ).bounds(centerX - 100, y + 75, 200, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("worldgate.button.report"),
                btn -> openLink(Constants.GITHUB_ISSUES)
        ).bounds(centerX - 100, y + 110, 97, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("worldgate.button.youtube"),
                btn -> openLink(Constants.YOUTUBE_CHANNEL)
        ).bounds(centerX + 3, y + 110, 97, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("worldgate.button.back"),
                btn -> goBack()
        ).bounds(centerX - 100, y + 140, 200, 20).build());
    }

    private void onCreate() {
        if (minecraft == null || minecraft.player == null) return;

        IntegratedServer server = minecraft.getSingleplayerServer();

        if (server == null) {
            sendMessage("WorldGate: no singleplayer world is running.");
            return;
        }

        sendMessage("WorldGate: creating room...");

        if (server.isPublished()) {
            int existingPort = server.getPort();

            if (existingPort <= 0 || existingPort > 65535) {
                sendMessage(
                        "WorldGate: existing published server has an invalid port."
                );
                return;
            }

            startWorldGateHost(server, existingPort);
            return;
        }

        int port = findFreePort();

        if (port <= 0) {
            sendMessage(
                    "WorldGate: could not find a free network port."
            );
            return;
        }

        sendMessage("WorldGate: using port " + port);

        boolean published = server.publishServer(
                GameType.DEFAULT_MODE,
                true,
                port
        );

        if (!published) {
            sendMessage(
                    "WorldGate: failed to publish the world on port " + port
            );
            return;
        }

        int publishedPort = server.getPort();

        if (publishedPort <= 0 || publishedPort > 65535) {
            sendMessage(
                    "WorldGate: Minecraft returned an invalid published port."
            );
            return;
        }

        startWorldGateHost(server, publishedPort);
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            return -1;
        }
    }

    private void startWorldGateHost(
            IntegratedServer server,
            int port
    ) {
        if (!HostBridge.start(port)) {
            sendMessage(
                    "WorldGate: failed to start host bridge on port " + port
            );
            return;
        }

        sendMessage(
                "WorldGate: Minecraft server published on port " + port
        );

        WorldGateModClient.EXECUTOR.submit(() -> {

            String hostAddress = HostBridge.getAdvertiseAddress();

            String code =
                    WorldGateModClient.ROOM_MANAGER.createRoom(
                            hostAddress,
                            port
                    );

            if (code == null) {
                HostBridge.stop();

                minecraft.execute(() ->
                        sendMessage(
                                "WorldGate: failed to create room."
                        )
                );

                return;
            }

            String hostName = minecraft.getUser().getName();

            WorldGateModClient.ROOM_MANAGER.playerJoin(
                    code,
                    hostName
            );

            boolean relayStarted = HostBridge.startRelay(code);

            WorldGateModClient.CURRENT_ROOM_CODE = code;
            WorldGateModClient.startHeartbeat(true);

            minecraft.execute(() -> {

                if (!relayStarted) {
                    sendMessage(
                            "WorldGate: relay could not start; LAN fallback is available."
                    );
                }

                sendMessage(
                        "WorldGate: Room Code = " + code
                );

                startRoomListeners(code);
            });
        });
    }

    private void onJoin() {
        String code = roomCodeBox.getValue()
                .trim()
                .toUpperCase();

        if (code.isEmpty()) return;

        sendMessage("WorldGate: joining...");

        WorldGateModClient.EXECUTOR.submit(() -> {

            String roomJson =
                    WorldGateModClient.ROOM_MANAGER.getRoom(code);

            minecraft.execute(() -> {

                if (roomJson == null || roomJson.equals("null")) {
                    sendMessage("WorldGate: room not found.");
                    return;
                }

                String hostAddress =
                        extractJsonString(roomJson, "hostAddress");

                int hostPort =
                        extractJsonInt(roomJson, "hostPort");

                if (hostAddress == null
                        || hostAddress.isBlank()
                        || hostPort <= 0
                        || hostPort > 65535) {

                    sendMessage(
                            "WorldGate: invalid host address."
                    );
                    return;
                }

                String ign = minecraft.getUser().getName();

                boolean joined =
                        WorldGateModClient.ROOM_MANAGER.playerJoin(
                                code,
                                ign
                        );

                if (!joined) {
                    sendMessage(
                            "WorldGate: could not register you in the room."
                    );
                    return;
                }

                WorldGateModClient.CURRENT_ROOM_CODE = code;
                WorldGateModClient.startHeartbeat(false);

                int relayPort =
                        RelayBridge.startPlayer(code);

                ServerAddress address =
                        relayPort > 0
                                ? new ServerAddress(
                                        "127.0.0.1",
                                        relayPort
                                )
                                : new ServerAddress(
                                        hostAddress,
                                        hostPort
                                );

                ServerData serverData =
                        new ServerData(
                                "WorldGate " + code,
                                address.toString(),
                                ServerData.Type.OTHER
                        );

                String connectionType =
                        relayPort > 0
                                ? "Internet relay"
                                : "LAN fallback";

                sendMessage(
                        "WorldGate: connecting via "
                                + connectionType
                                + " to "
                                + address.getHost()
                                + ":"
                                + address.getPort()
                );

                ConnectScreen.startConnecting(
                        this,
                        minecraft,
                        address,
                        serverData,
                        false,
                        null
                );
            });
        });
    }

    private void startRoomListeners(String code) {
        roomSnapshotInitialized = false;
        knownPlayers.clear();

        WorldGateModClient.ROOM_MANAGER.setRoomChangedListener(
                this::handleRoomChanged
        );

        WorldGateModClient.ROOM_MANAGER.startRealtime(code);

        WorldGateModClient.CHAT_MANAGER.listen(
                code,
                (uid, text) ->
                        minecraft.execute(() ->
                                sendMessage(
                                        "<" + shortUid(uid) + "> " + text
                                )
                        )
        );

        WorldGateModClient.EMOTE_MANAGER.listen(
                code,
                (uid, emote) ->
                        minecraft.execute(() ->
                                sendMessage(
                                        shortUid(uid) + " " + emote
                                )
                        )
        );
    }

    private void handleRoomChanged(String roomJson) {
        if (roomJson == null || roomJson.equals("null")) return;

        try {
            JsonObject room =
                    JsonParser.parseString(roomJson).getAsJsonObject();

            if (!room.has("players")
                    || !room.get("players").isJsonObject()) {
                return;
            }

            JsonObject players =
                    room.getAsJsonObject("players");

            Map<String, Boolean> current =
                    new HashMap<>();

            for (String uid : players.keySet()) {
                JsonObject player =
                        players.getAsJsonObject(uid);

                boolean online =
                        player.has("online")
                                && player.get("online").getAsBoolean();

                current.put(uid, online);
            }

            if (!roomSnapshotInitialized) {
                knownPlayers.clear();
                knownPlayers.putAll(current);
                roomSnapshotInitialized = true;
                return;
            }

            for (String uid : current.keySet()) {
                boolean online = current.get(uid);
                boolean wasOnline =
                        knownPlayers.getOrDefault(uid, false);

                if (online && !wasOnline) {
                    JsonObject player =
                            players.getAsJsonObject(uid);

                    String name =
                            player.has("ign")
                                    ? player.get("ign").getAsString()
                                    : shortUid(uid);

                    minecraft.execute(() ->
                            sendMessage(
                                    "WorldGate: " + name
                                            + " joined the room."
                            )
                    );
                }
            }

            for (String uid : knownPlayers.keySet()) {
                boolean wasOnline = knownPlayers.get(uid);

                boolean online =
                        current.getOrDefault(uid, false);

                if (wasOnline && !online) {
                    JsonObject player =
                            players.has(uid)
                                    && players.get(uid).isJsonObject()
                                    ? players.getAsJsonObject(uid)
                                    : null;

                    String name =
                            player != null && player.has("ign")
                                    ? player.get("ign").getAsString()
                                    : shortUid(uid);

                    minecraft.execute(() ->
                            sendMessage(
                                    "WorldGate: " + name
                                            + " left the room."
                            )
                    );
                }
            }

            knownPlayers.clear();
            knownPlayers.putAll(current);

        } catch (Exception e) {
            Constants.class.getName();
        }
    }

    private static String extractJsonString(
            String json,
            String key
    ) {
        if (json == null || key == null) return null;

        try {
            JsonObject object =
                    JsonParser.parseString(json).getAsJsonObject();

            if (!object.has(key)
                    || object.get(key).isJsonNull()) {
                return null;
            }

            return object.get(key).getAsString();

        } catch (Exception ignored) {
            return null;
        }
    }

    private static int extractJsonInt(
            String json,
            String key
    ) {
        if (json == null || key == null) return -1;

        try {
            JsonObject object =
                    JsonParser.parseString(json).getAsJsonObject();

            if (!object.has(key)
                    || object.get(key).isJsonNull()) {
                return -1;
            }

            return object.get(key).getAsInt();

        } catch (Exception ignored) {
            return -1;
        }
    }

    private static String shortUid(String uid) {
        if (uid == null || uid.isBlank()) return "?";

        return uid.substring(
                0,
                Math.min(6, uid.length())
        );
    }

    private void sendMessage(String message) {
        if (minecraft == null) return;

        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(
                    Component.literal(message)
            );
        } else {
            minecraft.setScreen(
                    new WorldGateScreenWithMessage(
                            this,
                            message
                    )
            );
        }
    }

    private void openLink(String url) {
        minecraft.setScreen(
                new ConfirmLinkScreen(
                        confirmed ->
                                minecraft.setScreen(this),
                        url,
                        true
                )
        );
    }

    private void goBack() {
        if (parent != null) {
            minecraft.setScreen(parent);
        } else {
            minecraft.setScreen(null);
        }
    }

    @Override
    public void onClose() {
        goBack();
    }

    private static class WorldGateScreenWithMessage
            extends Screen {

        private final Screen parentScreen;
        private final String message;

        private WorldGateScreenWithMessage(
                Screen parentScreen,
                String message
        ) {
            super(Component.literal("WorldGate"));
            this.parentScreen = parentScreen;
            this.message = message;
        }

        @Override
        protected void init() {
            int centerX = width / 2;

            addRenderableWidget(
                    Button.builder(
                            Component.literal("Back"),
                            btn -> minecraft.setScreen(parentScreen)
                    )
                    .bounds(
                            centerX - 100,
                            height / 2 + 30,
                            200,
                            20
                    )
                    .build()
            );
        }

        @Override
        public void render(
                net.minecraft.client.gui.GuiGraphics graphics,
                int mouseX,
                int mouseY,
                float delta
        ) {
            renderBackground(
                    graphics,
                    mouseX,
                    mouseY,
                    delta
            );

            graphics.drawCenteredString(
                    font,
                    "WorldGate",
                    width / 2,
                    height / 2 - 30,
                    0xFFFFFF
            );

            graphics.drawCenteredString(
                    font,
                    message,
                    width / 2,
                    height / 2,
                    0xAAAAAA
            );

            super.render(
                    graphics,
                    mouseX,
                    mouseY,
                    delta
            );
        }
    }
}
