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
    }

    @Override
    protected void init() {
        int left = 22;
        int top = 72;
        int gap = 8;
        int buttonW = Math.min(190, Math.max(150, (width - 120) / 4));
        int rowH = 29;

        roomCodeBox = new EditBox(
                font, left, top, buttonW - 68, 20,
                Component.translatable("worldgate.roomcode.hint")
        );
        roomCodeBox.setMaxLength(6);
        roomCodeBox.setHint(Component.literal("Room Code"));
        addRenderableWidget(roomCodeBox);

        addRenderableWidget(Button.builder(
                Component.translatable("worldgate.button.join"), b -> onJoin()
        ).bounds(left + buttonW - 62, top, 62, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("worldgate.button.create"), b -> onCreate()
        ).bounds(left, top + rowH, buttonW, 20).build());

        int c2 = left + buttonW + gap;
        int c3 = c2 + buttonW + gap;
        int c4 = c3 + buttonW + gap;

        addRenderableWidget(Button.builder(Component.literal("Profile"),
                b -> minecraft.setScreen(new EliteProfileScreen(this)))
                .bounds(c2, top, buttonW, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("worldgate.button.friends"),
                b -> minecraft.setScreen(new FriendsScreen(this)))
                .bounds(c2, top + rowH, buttonW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Chat"),
                b -> {
                    String room = WorldGateModClient.CURRENT_ROOM_CODE;
                    if (room != null && !room.isBlank()) minecraft.setScreen(new ChatScreen(this, room));
                    else sendMessage("Join or create a room first.");
                }).bounds(c2, top + rowH * 2, buttonW, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Elite Store"),
                b -> minecraft.setScreen(new EliteCoinScreen(this)))
                .bounds(c3, top, buttonW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cosmetics"),
                b -> minecraft.setScreen(new WardrobeScreen(this, WardrobeScreen.Tab.COSMETICS)))
                .bounds(c3, top + rowH, buttonW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Emotes"),
                b -> minecraft.setScreen(new WardrobeScreen(this, WardrobeScreen.Tab.EMOTES)))
                .bounds(c3, top + rowH * 2, buttonW, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("worldgate.button.lobby"),
                b -> minecraft.setScreen(new LobbyScreen(this)))
                .bounds(c4, top, buttonW, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("worldgate.button.claim_center"),
                b -> minecraft.setScreen(new ClaimCenterScreen(this)))
                .bounds(c4, top + rowH, buttonW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Settings"),
                b -> minecraft.setScreen(new SettingsScreen(this)))
                .bounds(c4, top + rowH * 2, buttonW, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("worldgate.button.report"),
                b -> openLink(Constants.GITHUB_ISSUES)
        ).bounds(width / 2 - 105, height - 58, 100, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("worldgate.button.youtube"),
                b -> openLink(Constants.YOUTUBE_CHANNEL)
        ).bounds(width / 2 + 5, height - 58, 100, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("worldgate.button.back"),
                b -> goBack()
        ).bounds(width / 2 - 100, height - 30, 200, 20).build());
    }

    private void onCreate() {

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

        graphics.centeredText(font, "WorldGate", width / 2, 20, 0xFFFFFFFF);
        graphics.centeredText(font, "Play together. Stay social. Express yourself.", width / 2, 38, 0xFF9AA7B4);

        int left = 22;
        int top = 60;
        int gap = 8;
        int buttonW = Math.min(190, Math.max(150, (width - 120) / 4));
        int panelH = 112;

        int[] xs = {left, left + buttonW + gap, left + (buttonW + gap) * 2, left + (buttonW + gap) * 3};
        String[] titles = {"PLAY TOGETHER", "SOCIAL", "ELITE", "MORE"};
        int[] colors = {0xFF7DE2FF, 0xFF70E090, 0xFFFFD166, 0xFFB8A7FF};

        for (int i = 0; i < 4; i++) {
            graphics.fill(xs[i], top, xs[i] + buttonW, top + panelH, 0xCC10161D);
            graphics.outline(xs[i], top, buttonW, panelH, 0xFF2B3742);
            graphics.text(font, titles[i], xs[i] + 10, top + 10, colors[i]);
        }

        graphics.text(font, "Join with a code or host your world.", xs[0] + 10, top + 91, 0xFF8E9AA6);
        graphics.text(font, "Friends, chat and live presence.", xs[1] + 10, top + 91, 0xFF8E9AA6);
        graphics.text(font, "Store, cosmetics and emotes.", xs[2] + 10, top + 91, 0xFF8E9AA6);
        graphics.text(font, "Lobby, claims and settings.", xs[3] + 10, top + 91, 0xFF8E9AA6);

        int infoY = top + panelH + 14;
        int infoH = Math.max(88, height - infoY - 72);
        graphics.fill(left, infoY, width - left, infoY + infoH, 0xCC0D1218);
        graphics.outline(left, infoY, width - left * 2, infoH, 0xFF26323D);

        String room = WorldGateModClient.CURRENT_ROOM_CODE;
        graphics.text(font, room == null || room.isBlank() ? "READY" : "ACTIVE ROOM",
                left + 14, infoY + 14, room == null || room.isBlank() ? 0xFF70E090 : 0xFF7DE2FF);
        graphics.text(font,
                room == null || room.isBlank()
                        ? "LAN discovery is available on the same Wi-Fi/hotspot."
                        : "Room " + room + " • realtime presence is active when connected.",
                left + 14, infoY + 34, 0xFFFFFFFF);
        graphics.text(font,
                "WorldGate keeps each feature on its own direct screen — no nested option overload.",
                left + 14, infoY + 51, 0xFF8E9AA6);

        if (minecraft != null && minecraft.player != null) {
            int x0 = width - left - 112;
            int y0 = infoY + 8;
            int x1 = width - left - 12;
            int y1 = Math.min(height - 72, infoY + infoH - 8);
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    graphics, x0, y0, x1, y1, 62, 0.0F, mouseX, mouseY, minecraft.player
            );
            graphics.text(font, minecraft.player.getName().getString(), x0, y1 - 2, 0xFFFFFFFF);
        }
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

        String currentRoom =
                WorldGateModClient.CURRENT_ROOM_CODE;

        WorldGateModClient.stopHeartbeat();

        if (
                currentRoom != null
                        && !currentRoom.isBlank()
        ) {

            final boolean wasHost = hostingRoom;

            WorldGateModClient.EXECUTOR.submit(
                    () -> {

                        try {
                            if (wasHost) {
                                WorldGateModClient
                                        .ROOM_MANAGER
                                        .hostLeave(currentRoom);
                                HostBridge.stop();
                            } else {
                                WorldGateModClient
                                        .ROOM_MANAGER
                                        .playerLeave(currentRoom);
                                RelayBridge.stop();
                            }
                        } catch (Exception ignored) {
                            RelayBridge.stop();
                            if (wasHost) {
                                HostBridge.stop();
                            }
                        }

                        WorldGateModClient
                                .CURRENT_ROOM_CODE =
                                null;
                    }
            );
        } else {

            RelayBridge.stop();
            if (hostingRoom) {
                HostBridge.stop();
            }

            WorldGateModClient
                    .CURRENT_ROOM_CODE =
                    null;
        }

        WorldGateSounds.play(WorldGateSounds.WORLD_DISCONNECT, 0.75F);
        hostingRoom = false;

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
