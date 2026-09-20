package com.rcraja.worldgate.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rcraja.worldgate.Constants;
import com.rcraja.worldgate.client.WorldGateModClient;
import com.rcraja.worldgate.network.HostBridge;
import com.rcraja.worldgate.network.RelayBridge;

import net.minecraft.util.Util;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;

public class WorldGateScreen extends Screen {

    private final Screen parent;
    private TextFieldWidget roomCodeBox;

    private final Map<String, Boolean> knownPlayers =
            new HashMap<>();

    private boolean roomSnapshotInitialized =
            false;

    private volatile String roomJson =
            null;

    public WorldGateScreen(Screen parent) {

        super(
                Text.translatable(
                        "worldgate.screen.title"
                )
        );

        this.parent =
                parent;
    }

    @Override
    protected void init() {

        int centerX =
                this.width / 2;

        int y =
                this.height / 2 - 95;

        roomCodeBox =
                new TextFieldWidget(
                        this.textRenderer,
                        centerX - 100,
                        y,
                        140,
                        20,
                        Text.translatable(
                                "worldgate.roomcode.hint"
                        )
                );

        roomCodeBox.setMaxLength(6);

        roomCodeBox.setPlaceholder(
                Text.translatable(
                        "worldgate.roomcode.hint"
                )
        );

        addDrawableChild(
                roomCodeBox
        );

        addDrawableChild(
                ButtonWidget.builder(
                        Text.translatable(
                                "worldgate.button.join"
                        ),
                        btn -> onJoin()
                )
                .dimensions(
                        centerX + 45,
                        y,
                        55,
                        20
                )
                .build()
        );

        ButtonWidget createButton =
                ButtonWidget.builder(
                        Text.translatable(
                                "worldgate.button.create"
                        ),
                        btn -> onCreate()
                )
                .dimensions(
                        centerX - 100,
                        y + 25,
                        200,
                        20
                )
                .build();

        createButton.active =
                this.client != null
                        && this.client.player != null
                        && this.client.getServer() != null;

        addDrawableChild(
                createButton
        );

        addDrawableChild(
                ButtonWidget.builder(
                        Text.translatable(
                                "worldgate.button.friends"
                        ),
                        btn ->
                                this.client.setScreen(
                                        new FriendsScreen(this)
                                )
                )
                .dimensions(
                        centerX - 100,
                        y + 50,
                        200,
                        20
                )
                .build()
        );

        addDrawableChild(
                ButtonWidget.builder(
                        Text.translatable(
                                "worldgate.button.lobby"
                        ),
                        btn ->
                                this.client.setScreen(
                                        new LobbyScreen(this)
                                )
                )
                .dimensions(
                        centerX - 100,
                        y + 75,
                        200,
                        20
                )
                .build()
        );

        addDrawableChild(
                ButtonWidget.builder(
                        Text.translatable(
                                "worldgate.button.report"
                        ),
                        btn ->
                                openLink(
                                        Constants.GITHUB_ISSUES
                                )
                )
                .dimensions(
                        centerX - 100,
                        y + 110,
                        97,
                        20
                )
                .build()
        );

        addDrawableChild(
                ButtonWidget.builder(
                        Text.translatable(
                                "worldgate.button.youtube"
                        ),
                        btn ->
                                openLink(
                                        Constants.YOUTUBE_CHANNEL
                                )
                )
                .dimensions(
                        centerX + 3,
                        y + 110,
                        97,
                        20
                )
                .build()
        );

        addDrawableChild(
                ButtonWidget.builder(
                        Text.translatable(
                                "worldgate.button.back"
                        ),
                        btn -> goBack()
                )
                .dimensions(
                        centerX - 100,
                        y + 140,
                        200,
                        20
                )
                .build()
        );
    }

    private void onCreate() {

        if (
                client == null
                        || client.player == null
        ) {
            return;
        }

        IntegratedServer server =
                client.getServer();

        if (server == null) {

            sendMessage(
                    "WorldGate: no singleplayer world is running."
            );

            return;
        }

        sendMessage(
                "WorldGate: creating room..."
        );

        if (HostBridge.isRunning()) {

            int existingPort =
                    server.getServerPort();

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
                server.openToLan(
                        GameMode.SURVIVAL,
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
                server.getServerPort();

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

                        client.execute(
                                () ->
                                        sendMessage(
                                                "WorldGate: failed to create room."
                                        )
                        );

                        return;
                    }

                    String hostName =
                            client
                                    .getUser()
                                    .getName();

                    WorldGateModClient
                            .ROOM_MANAGER
                            .playerJoin(
                                    code,
                                    hostName
                            );

                    boolean relayStarted =
                            HostBridge
                                    .startRelay(code);

                    WorldGateModClient
                            .CURRENT_ROOM_CODE =
                            code;

                    WorldGateModClient
                            .startHeartbeat(true);

                    client.execute(
                            () -> {

                                if (!relayStarted) {

                                    sendMessage(
                                            "WorldGate: relay could not start; LAN fallback is available."
                                    );
                                }

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

    private void onJoin() {

        String code =
                roomCodeBox
                        .getText()
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

                    client.execute(
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
                                        client
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

                                    sendMessage(
                                            "WorldGate: could not register you in the room."
                                    );

                                    return;
                                }

                                WorldGateModClient
                                        .CURRENT_ROOM_CODE =
                                        code;

                                WorldGateModClient
                                        .startHeartbeat(false);

                                int relayPort =
                                        RelayBridge
                                                .startPlayer(
                                                        code
                                                );

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

                                ServerInfo serverData =
                                        new ServerInfo(
                                                "WorldGate "
                                                        + code,
                                                address.toString(),
                                                ServerInfo.ServerType.OTHER
                                        );

                                String connectionType =
                                        relayPort > 0
                                                ? "Internet relay"
                                                : "LAN fallback";

                                sendMessage(
                                        "WorldGate: connecting via "
                                                + connectionType
                                                + " to "
                                                + address.getAddress()
                                                + ":"
                                                + address.getPort()
                                );

                                ConnectScreen.connect(
                                        this,
                                        client,
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

        WorldGateModClient.CHAT_MANAGER
                .listen(
                        code,
                        (uid, text) ->
                                client.execute(
                                        () ->
                                                sendMessage(
                                                        "<"
                                                                + playerName(uid)
                                                                + "> "
                                                                + text
                                                )
                                )
                );

        WorldGateModClient.EMOTE_MANAGER
                .listen(
                        code,
                        (uid, emote) ->
                                client.execute(
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

                    client.execute(
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

                    client.execute(
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
                client != null
                        && client.player != null
        ) {

            client.player.sendMessage(Text.literal(message), false);

        } else if (client != null) {

            client.setScreen(
                    new WorldGateScreenWithMessage(
                            parent,
                            message
                    )
            );
        }
    }

    private void openLink(String url) {

        if (client == null) {
            return;
        }

        client.setScreen(
                new ConfirmLinkScreen(
                        confirmed -> {

                            if (confirmed) {

                                try {

                                    Util.getOperatingSystem().open(url);

                                } catch (Exception ignored) {
                                }
                            }

                            client.setScreen(
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

        stopWorldGateRealtime();

        String currentRoom =
                WorldGateModClient.CURRENT_ROOM_CODE;

        WorldGateModClient.stopHeartbeat();

        if (
                currentRoom != null
                        && !currentRoom.isBlank()
        ) {

            WorldGateModClient.EXECUTOR.submit(
                    () -> {

                        try {

                            WorldGateModClient
                                    .ROOM_MANAGER
                                    .playerLeave(
                                            currentRoom
                                    );

                        } catch (Exception ignored) {
                        }

                        WorldGateModClient
                                .CURRENT_ROOM_CODE =
                                null;
                    }
            );
        } else {

            WorldGateModClient
                    .CURRENT_ROOM_CODE =
                    null;
        }

        if (client != null) {

            client.setScreen(
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
                    Text.literal(
                            "WorldGate"
                    )
            );

            this.parent =
                    parent;

            this.message =
                    message;
        }

        @Override
        protected void init() {

            addDrawableChild(
                    ButtonWidget.builder(
                            Text.literal(
                                    "Back"
                            ),
                            btn ->
                                    this.client
                                            .setScreen(
                                                    parent
                                            )
                    )
                    .dimensions(
                            this.width / 2 - 100,
                            this.height / 2 + 30,
                            200,
                            20
                    )
                    .build()
            );
        }

        @Override
        public void render(
                DrawContext graphics,
                int mouseX,
                int mouseY,
                float delta
        ) {

            super.render(
                    graphics,
                    mouseX,
                    mouseY,
                    delta
            );

            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal(
                            "WorldGate"
                    ),
                    this.width / 2,
                    this.height / 2 - 25,
                    0xFFFFFF
            );

            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal(
                            message
                    ),
                    this.width / 2,
                    this.height / 2,
                    0xFFFFFF
            );
        }
    }
}
