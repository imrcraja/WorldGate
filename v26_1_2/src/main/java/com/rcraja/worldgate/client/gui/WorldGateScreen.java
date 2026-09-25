package com.rcraja.worldgate.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rcraja.worldgate.Constants;
import com.rcraja.worldgate.client.WorldGateModClient;
import com.rcraja.worldgate.client.WorldGateSkinCache;
import com.rcraja.worldgate.network.HostBridge;
import com.rcraja.worldgate.network.RelayBridge;
import com.rcraja.worldgate.client.WorldGateSounds;

import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
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

    private boolean roomSnapshotInitialized =
            false;

    private volatile String roomJson =
            null;

    private boolean hostingRoom = false;

    public WorldGateScreen(Screen parent) {

        super(
                Component.translatable(
                        "worldgate.screen.title"
                )
        );

        this.parent =
                parent;
        WorldGateSounds.play(WorldGateSounds.WORLDGATE_OPEN, 0.85F);
        WorldGateModClient.syncCurrentSkin();
    }

    @Override
    protected void init() {
        /*
         * WorldGate is intentionally organized as a small dashboard:
         * PLAY, SOCIAL, ELITE and MORE each get their own area.
         * Keep controls inside their card instead of stacking one large
         * collection of equally-sized buttons.
         */
        int margin = Math.max(28, width / 24);
        int gap = 14;
        int contentTop = 70;
        int cardW = Math.max(220, (width - margin * 2 - gap) / 2);
        int cardH = 154;

        int leftX = margin;
        int rightX = margin + cardW + gap;
        int topY = contentTop;
        int bottomY = contentTop + cardH + gap;

        int inputW = Math.max(150, cardW - 116);
        roomCodeBox = new EditBox(
                font, leftX + 14, topY + 48, inputW, 20,
                Component.translatable("worldgate.roomcode.hint")
        );
        roomCodeBox.setMaxLength(10);
        roomCodeBox.setFilter(value -> value.matches("\\d{0,10}"));
        roomCodeBox.setHint(Component.literal("Room Code"));
        addRenderableWidget(roomCodeBox);

        addRenderableWidget(Button.builder(
                Component.translatable("worldgate.button.join"), b -> onJoin()
        ).bounds(leftX + 20 + inputW, topY + 48, 70, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("worldgate.button.create"), b -> onCreate()
        ).bounds(leftX + 14, topY + 82, cardW - 28, 24).build());

        addRenderableWidget(Button.builder(
                Component.literal("Profile"),
                b -> minecraft.setScreen(new EliteProfileScreen(this))
        ).bounds(rightX + 14, topY + 48, (cardW - 42) / 3, 24).build());

        addRenderableWidget(Button.builder(
                Component.translatable("worldgate.button.friends"),
                b -> minecraft.setScreen(new FriendsScreen(this))
        ).bounds(rightX + 21 + (cardW - 42) / 3, topY + 48,
                (cardW - 42) / 3, 24).build());

        addRenderableWidget(Button.builder(
                Component.literal("Chat"),
                b -> {
                    String room = WorldGateModClient.CURRENT_ROOM_CODE;
                    if (room != null && !room.isBlank()) {
                        minecraft.setScreen(new ChatScreen(this, room));
                    } else {
                        sendMessage("Join or create a room first.");
                    }
                }).bounds(rightX + 28 + ((cardW - 42) / 3) * 2, topY + 48,
                (cardW - 42) / 3, 24).build());

        addRenderableWidget(Button.builder(
                Component.translatable("worldgate.button.lobby"),
                b -> minecraft.setScreen(new LobbyScreen(this))
        ).bounds(leftX + 14, bottomY + 48, (cardW - 42) / 3, 24).build());

        addRenderableWidget(Button.builder(
                Component.translatable("worldgate.button.claim_center"),
                b -> minecraft.setScreen(new ClaimCenterScreen(this))
        ).bounds(leftX + 21 + (cardW - 42) / 3, bottomY + 48, (cardW - 42) / 3, 24).build());

        addRenderableWidget(Button.builder(
                Component.literal("Settings"),
                b -> minecraft.setScreen(new SettingsScreen(this))
        ).bounds(leftX + 28 + ((cardW - 42) / 3) * 2, bottomY + 48,
                (cardW - 42) / 3, 24).build());

        addRenderableWidget(Button.builder(
                Component.literal("Elite Store"),
                b -> minecraft.setScreen(new EliteCoinScreen(this))
        ).bounds(rightX + 14, bottomY + 48, cardW - 28, 24).build());

        addRenderableWidget(Button.builder(
                Component.literal("Cosmetics"),
                b -> minecraft.setScreen(new WardrobeScreen(this, WardrobeScreen.Tab.COSMETICS))
        ).bounds(rightX + 14, bottomY + 82, (cardW - 42) / 2, 24).build());

        addRenderableWidget(Button.builder(
                Component.literal("Emotes"),
                b -> minecraft.setScreen(new WardrobeScreen(this, WardrobeScreen.Tab.EMOTES))
        ).bounds(rightX + 21 + (cardW - 42) / 2, bottomY + 82,
                (cardW - 42) / 2, 24).build());

        addRenderableWidget(Button.builder(
                Component.translatable("worldgate.button.report"),
                b -> openLink(Constants.GITHUB_ISSUES)
        ).bounds(width / 2 - 300, height - 30, 90, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("worldgate.button.youtube"),
                b -> openLink(Constants.YOUTUBE_CHANNEL)
        ).bounds(width / 2 + 210, height - 30, 90, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("worldgate.button.back"),
                b -> goBack()
        ).bounds(width / 2 - 100, height - 30, 200, 20).build());
    }

    private void onCreate() {

        // A hosted world's room code is stable until that world actually disconnects.
        String activeHostRoom = WorldGateModClient.HOSTING_ROOM_CODE;
        if (activeHostRoom != null && !activeHostRoom.isBlank()) {
            roomCodeBox.setValue(activeHostRoom);
            sendMessage("WorldGate: already hosting Room Code = " + activeHostRoom);
            return;
        }

        if (
                minecraft == null
                        || minecraft.player == null
        ) {
            return;
        }

        IntegratedServer server =
                minecraft.getSingleplayerServer();

        if (server == null) {

            sendMessage(
                    "WorldGate: no singleplayer world is running."
            );

            return;
        }

        sendMessage(
                "WorldGate: creating room..."
        );

        if (server.isPublished()) {

            int existingPort =
                    server.getPort();

            if (
                    existingPort <= 0
                            || existingPort > 65535
            ) {

                sendMessage(
                        "WorldGate: existing published server has an invalid port."
                );

                return;
            }

            startWorldGateHost(
                    server,
                    existingPort
            );

            return;
        }

        int port =
                findFreePort();

        if (port <= 0) {

            sendMessage(
                    "WorldGate: could not find a free network port."
            );

            return;
        }

        sendMessage(
                "WorldGate: using port "
                        + port
        );

        boolean published =
                server.publishServer(
                        GameType.SURVIVAL,
                        true,
                        port
                );

        if (!published) {

            sendMessage(
                    "WorldGate: failed to publish the world on port "
                            + port
            );

            return;
        }

        int publishedPort =
                server.getPort();

        if (
                publishedPort <= 0
                        || publishedPort > 65535
        ) {

            sendMessage(
                    "WorldGate: Minecraft returned an invalid published port."
            );

            return;
        }

        startWorldGateHost(
                server,
                publishedPort
        );
    }

    private static int findFreePort() {

        try (
                ServerSocket socket =
                        new ServerSocket(0)
        ) {

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
                    "WorldGate: failed to start host bridge on port "
                            + port
            );

            return;
        }

        sendMessage(
                "WorldGate: Minecraft server published on port "
                        + port
        );

        WorldGateModClient.EXECUTOR.submit(
                () -> {

                    String hostAddress =
                            HostBridge.getAdvertiseAddress();

                    String code =
                            WorldGateModClient
                                    .ROOM_MANAGER
                                    .createRoom(
                                            hostAddress,
                                            port
                                    );

                    if (code == null) {

                        HostBridge.stop();

                        minecraft.execute(
                                () ->
                                        sendMessage(
                                                "WorldGate: failed to create room."
                                        )
                        );

                        return;
                    }

                    String hostName =
                            minecraft
                                    .getUser()
                                    .getName();

                    WorldGateModClient
                            .ROOM_MANAGER
                            .playerJoin(
                                    code,
                                    hostName
                            );

                    HostBridge.startLanDiscovery(code, hostName);

                    boolean relayStarted =
                            WorldGateModClient.useInternetRelay()
                                    && HostBridge.startRelay(code);

                    hostingRoom = true;

                    WorldGateModClient
                            .CURRENT_ROOM_CODE =
                            code;
                    WorldGateModClient
                            .HOSTING_ROOM_CODE =
                            code;

                    WorldGateModClient
                            .startHeartbeat(true);
                    WorldGateSkinCache.refreshRoomPlayers(
                            WorldGateModClient.ROOM_MANAGER.getRoom(code)
                    );

                    minecraft.execute(
                            () -> {

                                if (!relayStarted) {

                                    sendMessage(
                                            "WorldGate: relay could not start; LAN fallback is available."
                                    );
                                }

                                WorldGateSounds.play(WorldGateSounds.WORLD_CONNECT, 0.9F);
                                sendMessage(
                                        "WorldGate: Room Code = "
                                                + code
                                );

                                startRoomListeners(
                                        code
                                );
                            }
                    );
                }
        );
    }

    public void joinRoomFromInvite(String code) {
        if (code == null || code.isBlank()) {
            sendMessage("WorldGate: invite has no room code.");
            return;
        }
        roomCodeBox.setValue(code.trim().toUpperCase());
        onJoin();
    }

    private void onJoin() {

        String code =
                roomCodeBox
                        .getValue()
                        .trim()
                        .toUpperCase();

        if (code.isEmpty()) {
            return;
        }

        sendMessage(
                "WorldGate: joining..."
        );

        WorldGateModClient.EXECUTOR.submit(
                () -> {

                    String roomJson =
                            WorldGateModClient
                                    .ROOM_MANAGER
                                    .getRoom(code);

                    minecraft.execute(
                            () -> {

                                if (
                                        roomJson == null
                                                || roomJson.equals("null")
                                ) {

                                    sendMessage(
                                            "WorldGate: room not found."
                                    );

                                    return;
                                }

                                String hostAddress =
                                        extractJsonString(
                                                roomJson,
                                                "hostAddress"
                                        );

                                int hostPort =
                                        extractJsonInt(
                                                roomJson,
                                                "hostPort"
                                        );

                                if (
                                        hostAddress == null
                                                || hostAddress.isBlank()
                                                || hostPort <= 0
                                                || hostPort > 65535
                                ) {

                                    sendMessage(
                                            "WorldGate: invalid host address."
                                    );

                                    return;
                                }

                                String ign =
                                        minecraft
                                                .getUser()
                                                .getName();

                                boolean joined =
                                        WorldGateModClient
                                                .ROOM_MANAGER
                                                .playerJoin(
                                                        code,
                                                        ign
                                                );

                                if (!joined) {
                                    minecraft.execute(
                                            () -> sendMessage(
                                                    "WorldGate: could not register you in the room."
                                            )
                                    );
                                    return;
                                }

                                /*
                                 * Probe the relay off the render thread. startPlayer()
                                 * performs the short WebSocket connection attempt so a
                                 * failed relay can immediately fall back to the host.
                                 */
                                int relayPort =
                                        WorldGateModClient.useInternetRelay()
                                                ? RelayBridge.startPlayer(code)
                                                : -1;

                                minecraft.execute(
                                        () -> {
                                            hostingRoom = false;

                                            WorldGateModClient
                                                    .CURRENT_ROOM_CODE =
                                                    code;

                                            WorldGateModClient
                                                    .startHeartbeat(false);
                                            WorldGateSkinCache.refreshRoomPlayers(roomJson);

                                            ServerAddress address;
                                            if (relayPort > 0) {
                                                address = new ServerAddress("127.0.0.1", relayPort);
                                            } else if (WorldGateModClient.allowLanFallback()) {
                                                address = new ServerAddress(hostAddress, hostPort);
                                            } else {
                                                sendMessage("WorldGate: Internet relay unavailable and LAN fallback is disabled.");
                                                WorldGateModClient.ROOM_MANAGER.playerLeave(code);
                                                return;
                                            }

                                            ServerData serverData =
                                                    new ServerData(
                                                            "WorldGate "
                                                                    + code,
                                                            address.toString(),
                                                            ServerData.Type.OTHER
                                                    );

                                            String connectionType =
                                                    relayPort > 0
                                                            ? "Internet relay"
                                                            : "LAN";

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
                                        }
                                );
                            }
                    );
                }
        );
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float delta
    ) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int margin = Math.max(28, width / 24);
        int gap = 14;
        int contentTop = 70;
        int cardW = Math.max(220, (width - margin * 2 - gap) / 2);
        int cardH = 154;
        int leftX = margin;
        int rightX = margin + cardW + gap;
        int bottomY = contentTop + cardH + gap;

        graphics.centeredText(font, "WorldGate", width / 2, 18, 0xFFFFFFFF);
        graphics.centeredText(font,
                "Play together. Stay social. Express yourself.",
                width / 2, 38, 0xFF9AA7B4);

        drawDashboardCard(graphics, leftX, contentTop, cardW, cardH, "PLAY TOGETHER", 0xFF7DE2FF);
        drawDashboardCard(graphics, rightX, contentTop, cardW, cardH, "SOCIAL", 0xFF70E090);
        drawDashboardCard(graphics, leftX, bottomY, cardW, cardH, "MORE", 0xFFB8A7FF);
        drawDashboardCard(graphics, rightX, bottomY, cardW, cardH, "ELITE", 0xFFFFD166);

        graphics.text(font, "Join a room or host your own world.", leftX + 14, contentTop + 30, 0xFF9AA7B4);
        graphics.text(font, "Profile, friends and live room chat.", rightX + 14, contentTop + 30, 0xFF9AA7B4);
        graphics.text(font, "Lobby, claims and WorldGate settings.", leftX + 14, bottomY + 30, 0xFF9AA7B4);
        graphics.text(font, "Elite Coins, cosmetics and emotes.", rightX + 14, bottomY + 30, 0xFF9AA7B4);

        String room = WorldGateModClient.CURRENT_ROOM_CODE;
        String status = room == null || room.isBlank()
                ? "READY • LAN discovery available on the same Wi-Fi/hotspot"
                : "ACTIVE • Room " + room + " • realtime presence enabled";
        graphics.centeredText(font, status, width / 2, height - 48, 0xFFB8C2CC);
    }

    private void drawDashboardCard(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int w,
            int h,
            String title,
            int accent
    ) {
        // No opaque fill: the Minecraft world stays visible behind the dashboard.
        graphics.outline(x, y, w, h, 0x662B3742);
        graphics.text(font, title, x + 14, y + 10, accent);
    }

    private void drawCard(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int w,
            int h
    ) {
        graphics.fill(x, y, x + w, y + h, 0xCC10161D);
        graphics.outline(x, y, w, h, 0xFF2B3742);
    }

    private void startRoomListeners(
            String code
    ) {

        roomSnapshotInitialized =
                false;

        knownPlayers.clear();

        roomJson =
                null;

        WorldGateModClient.ROOM_MANAGER
                .setRoomChangedListener(
                        json -> {

                            roomJson =
                                    json;

                            handleRoomChanged(
                                    json
                            );
                        }
                );

        WorldGateModClient.ROOM_MANAGER
                .startRealtime(code);

        WorldGateModClient.EMOTE_MANAGER
                .listen(
                        code,
                        (uid, emote) ->
                                minecraft.execute(
                                        () ->
                                                sendMessage(
                                                        playerName(uid)
                                                                + " "
                                                                + emote
                                                )
                                )
                );
    }

    private void handleRoomChanged(
            String roomJson
    ) {

        if (
                roomJson == null
                        || roomJson.equals("null")
        ) {
            return;
        }

        try {

            JsonObject room =
                    JsonParser
                            .parseString(roomJson)
                            .getAsJsonObject();

            if (
                    !room.has("players")
                            || !room.get("players")
                                    .isJsonObject()
            ) {
                return;
            }

            JsonObject players =
                    room.getAsJsonObject(
                            "players"
                    );

            Map<String, Boolean> current =
                    new HashMap<>();

            for (String uid :
                    players.keySet()) {

                JsonObject player =
                        players.getAsJsonObject(
                                uid
                        );

                boolean online =
                        player.has("online")
                                && player.get(
                                        "online"
                                ).getAsBoolean();

                current.put(
                        uid,
                        online
                );
            }

            if (!roomSnapshotInitialized) {

                knownPlayers.clear();

                knownPlayers.putAll(
                        current
                );

                roomSnapshotInitialized =
                        true;

                return;
            }

            for (String uid :
                    current.keySet()) {

                boolean online =
                        current.get(uid);

                boolean wasOnline =
                        knownPlayers.getOrDefault(
                                uid,
                                false
                        );

                if (
                        online
                                && !wasOnline
                ) {

                    String name =
                            playerName(
                                    players,
                                    uid
                            );

                    minecraft.execute(
                            () ->
                                    sendMessage(
                                            "WorldGate: "
                                                    + name
                                                    + " joined the room."
                                    )
                    );
                }
            }

            for (String uid :
                    knownPlayers.keySet()) {

                boolean wasOnline =
                        knownPlayers.get(uid);

                boolean online =
                        current.getOrDefault(
                                uid,
                                false
                        );

                if (
                        wasOnline
                                && !online
                ) {

                    String name =
                            playerName(
                                    players,
                                    uid
                            );

                    minecraft.execute(
                            () ->
                                    sendMessage(
                                            "WorldGate: "
                                                    + name
                                                    + " left the room."
                                    )
                    );
                }
            }

            knownPlayers.clear();

            knownPlayers.putAll(
                    current
            );

        } catch (Exception e) {

            Constants.class.getName();
        }
    }

    private String playerName(
            String uid
    ) {

        if (
                uid == null
                        || uid.isBlank()
        ) {

            return "Player";
        }

        String currentUid =
                WorldGateModClient
                        .FRIEND_MANAGER
                        .myUid();

        if (uid.equals(currentUid)) {

            return "You";
        }

        String name =
                playerNameFromRoomJson(
                        roomJson,
                        uid
                );

        if (
                name != null
                        && !name.isBlank()
        ) {

            return name;
        }

        return "Player";
    }

    private static String playerName(
            JsonObject players,
            String uid
    ) {

        try {

            if (
                    players != null
                            && players.has(uid)
                            && players.get(uid)
                                    .isJsonObject()
            ) {

                JsonObject player =
                        players.getAsJsonObject(
                                uid
                        );

                if (player.has("ign")) {

                    String ign =
                            player.get("ign")
                                    .getAsString();

                    if (
                            ign != null
                                    && !ign.isBlank()
                    ) {

                        return ign;
                    }
                }
            }

        } catch (Exception ignored) {
        }

        return "Player";
    }

    private static String playerNameFromRoomJson(
            String json,
            String uid
    ) {

        if (
                json == null
                        || json.isBlank()
                        || "null".equals(json)
                        || uid == null
        ) {

            return null;
        }

        try {

            JsonObject room =
                    JsonParser
                            .parseString(json)
                            .getAsJsonObject();

            if (
                    !room.has("players")
                            || !room.get("players")
                                    .isJsonObject()
            ) {

                return null;
            }

            JsonObject players =
                    room.getAsJsonObject(
                            "players"
                    );

            if (
                    !players.has(uid)
                            || !players.get(uid)
                                    .isJsonObject()
            ) {

                return null;
            }

            JsonObject player =
                    players.getAsJsonObject(
                            uid
                    );

            if (player.has("ign")) {

                String ign =
                        player.get("ign")
                                .getAsString();

                if (
                        ign != null
                                && !ign.isBlank()
                ) {

                    return ign;
                }
            }

        } catch (Exception ignored) {
        }

        return null;
    }

    private static String extractJsonString(
            String json,
            String key
    ) {

        if (
                json == null
                        || key == null
        ) {

            return null;
        }

        try {

            JsonObject object =
                    JsonParser
                            .parseString(json)
                            .getAsJsonObject();

            if (
                    !object.has(key)
                            || object.get(key)
                                    .isJsonNull()
            ) {

                return null;
            }

            return object.get(key)
                    .getAsString();

        } catch (Exception ignored) {

            return null;
        }
    }

    private static int extractJsonInt(
            String json,
            String key
    ) {

        if (
                json == null
                        || key == null
        ) {

            return -1;
        }

        try {

            JsonObject object =
                    JsonParser
                            .parseString(json)
                            .getAsJsonObject();

            if (
                    !object.has(key)
                            || object.get(key)
                                    .isJsonNull()
            ) {

                return -1;
            }

            return object.get(key)
                    .getAsInt();

        } catch (Exception ignored) {

            return -1;
        }
    }

    private void sendMessage(
            String message
    ) {

        if (
                minecraft != null
                        && minecraft.player != null
        ) {

            minecraft.player.sendSystemMessage(
                    Component.literal(message)
            );

        } else if (minecraft != null) {

            minecraft.setScreen(
                    new WorldGateScreenWithMessage(
                            parent,
                            message
                    )
            );
        }
    }

    private void openLink(String url) {

        if (minecraft == null) {
            return;
        }

        minecraft.setScreen(
                new ConfirmLinkScreen(
                        confirmed -> {

                            if (confirmed) {

                                try {

                                    Util.getPlatform()
                                            .openUri(url);

                                } catch (Exception ignored) {
                                }
                            }

                            minecraft.setScreen(
                                    this
                            );
                        },
                        url,
                        true
                )
        );
    }

    private void stopWorldGateRealtime() {

        try {
            WorldGateModClient.ROOM_MANAGER
                    .stopRealtime();
        } catch (Exception ignored) {
        }

        try {
            WorldGateModClient.CHAT_MANAGER
                    .stopListening();
        } catch (Exception ignored) {
        }

        try {
            WorldGateModClient.EMOTE_MANAGER
                    .stopListening();
        } catch (Exception ignored) {
        }
    }

    private void goBack() {

        WorldGateSounds.play(WorldGateSounds.WORLDGATE_CLOSE, 0.8F);
        stopWorldGateRealtime();

        /*
         * Leaving the WorldGate menu is NOT leaving the Minecraft world.
         * The room, heartbeat, relay and room code must stay alive while the
         * host minimizes/opens another screen. Actual world disconnect cleanup
         * is handled by ClientPacketListenerMixin.
         */
        if (minecraft != null) {

            minecraft.setScreen(
                    parent
            );
        }
    }

    @Override
    public void onClose() {
        goBack();
    }

    private static class WorldGateScreenWithMessage
            extends Screen {

        private final Screen parent;
        private final String message;

        protected WorldGateScreenWithMessage(
                Screen parent,
                String message
        ) {

            super(
                    Component.translatable("worldgate.screen.title")
            );

            this.parent =
                    parent;

            this.message =
                    message;
        }

        @Override
        protected void init() {

            addRenderableWidget(
                    Button.builder(
                            Component.translatable("worldgate.button.back"),
                            btn ->
                                    this.minecraft
                                            .setScreen(
                                                    parent
                                            )
                    )
                    .bounds(
                            this.width / 2 - 100,
                            this.height / 2 + 30,
                            200,
                            20
                    )
                    .build()
            );
        }

        @Override
        public void extractRenderState(
                GuiGraphicsExtractor graphics,
                int mouseX,
                int mouseY,
                float delta
        ) {

            super.extractRenderState(
                    graphics,
                    mouseX,
                    mouseY,
                    delta
            );

            graphics.centeredText(
                    this.font,
                    Component.translatable("worldgate.screen.title"),
                    this.width / 2,
                    this.height / 2 - 25,
                    0xFFFFFF
            );

            graphics.centeredText(
                    this.font,
                    Component.literal(
                            message
                    ),
                    this.width / 2,
                    this.height / 2,
                    0xFFFFFF
            );
        }
    }
}
