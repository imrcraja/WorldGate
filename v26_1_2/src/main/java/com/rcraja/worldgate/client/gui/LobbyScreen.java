package com.rcraja.worldgate.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rcraja.worldgate.client.WorldGateModClient;
import com.rcraja.worldgate.client.HostPermissionManager;
import com.rcraja.worldgate.client.elite.EliteBadgeRenderer;
import com.rcraja.worldgate.client.elite.EliteManager;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LobbyScreen extends Screen {

    private final Screen parent;
    private EditBox chatBox;
    private EditBox opPlayerBox;

    private final Map<String, FriendProfile> friendProfiles =
            new ConcurrentHashMap<>();

    private final Map<String, Integer> eliteLevels =
            new ConcurrentHashMap<>();

    private volatile String friendsJson = null;
    private volatile String roomJson = null;
    private volatile String boundRoom = "";

    private final List<String> chatMessages =
            new ArrayList<>();

    private volatile boolean loading = true;
    private volatile String status = "Connecting to WorldGate...";

    public LobbyScreen(Screen parent) {
        super(Component.translatable("worldgate.lobby.title"));
        this.parent = parent;
    }

    private static class FriendProfile {

        final String name;
        final String friendCode;
        final boolean online;

        FriendProfile(
                String name,
                String friendCode,
                boolean online
        ) {
            this.name = name;
            this.friendCode = friendCode;
            this.online = online;
        }
    }

    @Override
    protected void init() {

        int centerX =
                this.width / 2;

        int chatY =
                this.height - 42;

        chatBox =
                new EditBox(
                        this.font,
                        20,
                        chatY,
                        this.width - 110,
                        20,
                        Component.translatable("worldgate.chat.input")
                );

        chatBox.setMaxLength(200);

        opPlayerBox = new EditBox(this.font, 20, 58, 170, 20, Component.literal("Player IGN"));
        opPlayerBox.setMaxLength(16);
        opPlayerBox.setHint(Component.literal("Player IGN"));
        addRenderableWidget(opPlayerBox);
        addRenderableWidget(Button.builder(Component.literal("Grant OP"), btn -> setPlayerOp(true))
                .bounds(195, 58, 75, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Revoke OP"), btn -> setPlayerOp(false))
                .bounds(275, 58, 82, 20).build());

        chatBox.setHint(
                Component.translatable("worldgate.chat.hint")
        );

        addRenderableWidget(chatBox);

        addRenderableWidget(
                Button.builder(
                        Component.translatable("worldgate.chat.send"),
                        btn -> sendChat()
                )
                .bounds(
                        this.width - 85,
                        chatY,
                        65,
                        20
                )
                .build()
        );

        addRenderableWidget(
                Button.builder(Component.literal("Refresh"),
                        btn -> refreshNow())
                .bounds(centerX - 155, this.height - 20, 75, 20)
                .build()
        );

        addRenderableWidget(
                Button.builder(Component.literal("Friends"),
                        btn -> openFriends())
                .bounds(centerX - 75, this.height - 20, 75, 20)
                .build()
        );

        addRenderableWidget(
                Button.builder(Component.literal("Elite Store"),
                        btn -> minecraft.setScreen(new EliteCoinScreen(this)))
                .bounds(centerX + 5, this.height - 20, 90, 20)
                .build()
        );

        addRenderableWidget(
                Button.builder(
                        Component.translatable("worldgate.button.back"),
                        btn -> goBack()
                )
                .bounds(centerX + 100, this.height - 20, 70, 20)
                .build()
        );

        startRealtime();
    }

    private void setPlayerOp(boolean grant) {
        if (minecraft == null || minecraft.getSingleplayerServer() == null) {
            status = "OP control is host-only.";
            return;
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (!server.isPublished() || !WorldGateModClient.CURRENT_ROOM_CODE.equals(boundRoom)) {
            status = "Only the active WorldGate host can manage OP.";
            return;
        }
        String name = opPlayerBox == null ? "" : opPlayerBox.getValue().trim();
        if (name.isBlank()) {
            status = "Enter a player IGN first.";
            return;
        }
        var player = server.getPlayerList().getPlayerByName(name);
        if (player == null) {
            status = "Player is not currently connected.";
            return;
        }
        try {
            NameAndId target = new NameAndId(player.getGameProfile());
            if (grant) {
                server.getPlayerList().op(target);
                HostPermissionManager.grant(player.getUUID());
                status = "OP granted to " + name + " and saved on the host.";
            } else {
                server.getPlayerList().deop(target);
                HostPermissionManager.revoke(player.getUUID());
                status = "OP revoked from " + name + " and removed from host storage.";
            }
        } catch (Exception e) {
            status = "Could not change OP for " + name + ".";
        }
    }

    private void startRealtime() {

        loading = true;

        WorldGateModClient.FRIEND_MANAGER
                .setFriendListChangedListener(
                        json -> {

                            friendsJson = json;

                            if (this.minecraft != null) {

                                this.minecraft.execute(
                                        this::refreshFriendProfiles
                                );
                            }
                        }
                );

        WorldGateModClient.FRIEND_MANAGER
                .startRealtime();

        WorldGateModClient.EXECUTOR.submit(
                () -> {

                    String json =
                            WorldGateModClient
                                    .FRIEND_MANAGER
                                    .getFriends();

                    if (this.minecraft != null) {

                        this.minecraft.execute(
                                () -> {

                                    friendsJson =
                                            json;

                                    refreshFriendProfiles();
                                }
                        );
                    }
                }
        );

        bindRoomRealtime(WorldGateModClient.CURRENT_ROOM_CODE);

        loading = false;
        String currentRoom = WorldGateModClient.CURRENT_ROOM_CODE;
        status = currentRoom != null && !currentRoom.isBlank()
                ? "Connected • Room " + currentRoom
                : "Connected • No active room";
    }

    private void refreshNow() {
        status = "Refreshing...";
        loading = true;
        WorldGateModClient.EXECUTOR.submit(() -> {
            String json = WorldGateModClient.FRIEND_MANAGER.getFriends();
            String room = WorldGateModClient.CURRENT_ROOM_CODE;
            String roomData = room == null || room.isBlank()
                    ? null
                    : WorldGateModClient.ROOM_MANAGER.getRoom(room);
            if (minecraft != null) minecraft.execute(() -> {
                String previousRoom = boundRoom;
                friendsJson = json;
                roomJson = roomData;
                refreshFriendProfiles();

                if (!sameRoom(previousRoom, room)) {
                    bindRoomRealtime(room);
                }

                loading = false;
                status = room == null || room.isBlank()
                        ? "Refreshed • No active room"
                        : "Refreshed • Room " + room;
            });
        });
    }

    private static boolean sameRoom(String first, String second) {
        String a = first == null ? "" : first.trim();
        String b = second == null ? "" : second.trim();
        return a.equals(b);
    }

    private void bindRoomRealtime(String room) {
        WorldGateModClient.ROOM_MANAGER.stopRealtime();
        WorldGateModClient.CHAT_MANAGER.stopListening();
        roomJson = null;
        synchronized (chatMessages) {
            chatMessages.clear();
        }
        boundRoom = room == null ? "" : room.trim();

        if (room == null || room.isBlank()) {
            status = "Connected • No active room";
            return;
        }

        WorldGateModClient.ROOM_MANAGER.setRoomChangedListener(json -> {
            roomJson = json;
            if (minecraft != null) {
                minecraft.execute(() -> {
                    if (sameRoom(boundRoom, room)) {
                        status = "Connected • Room " + room;
                    }
                });
            }
        });
        WorldGateModClient.ROOM_MANAGER.startRealtime(room);

        WorldGateModClient.CHAT_MANAGER.listen(
                room,
                (uid, text) -> {
                    if (minecraft != null) {
                        minecraft.execute(() ->
                                addChatMessage("<" + displayName(uid) + "> " + text));
                    }
                }
        );
        status = "Connected • Room " + room;
    }

    private void openFriends() {
        if (minecraft != null) minecraft.setScreen(new FriendsScreen(this));
    }

    private void refreshFriendProfiles() {

        String json =
                friendsJson;

        if (json == null
                || json.equals("null")
                || json.isBlank()) {

            friendProfiles.clear();

            return;
        }

        try {

            JsonObject friends =
                    JsonParser
                            .parseString(json)
                            .getAsJsonObject();

            for (String uid :
                    friends.keySet()) {

                WorldGateModClient.EXECUTOR.submit(
                        () -> {

                            String profile =
                                    WorldGateModClient
                                            .FRIEND_MANAGER
                                            .getProfile(uid);

                            String name =
                                    "Player";

                            String friendCode =
                                    "WG------";

                            boolean online =
                                    false;

                            try {

                                if (profile != null
                                        && !profile.equals("null")
                                        && !profile.isBlank()) {

                                    JsonObject object =
                                            JsonParser
                                                    .parseString(profile)
                                                    .getAsJsonObject();

                                    if (object.has(
                                            "displayName"
                                    )) {

                                        name =
                                                object.get(
                                                        "displayName"
                                                ).getAsString();
                                    }

                                    if (object.has(
                                            "friendCode"
                                    )) {

                                        friendCode =
                                                object.get(
                                                        "friendCode"
                                                ).getAsString();
                                    }

                                    if (object.has(
                                            "online"
                                    )) {

                                        online =
                                                object.get(
                                                        "online"
                                                ).getAsBoolean();
                                    }
                                }

                            } catch (Exception ignored) {
                            }

                            final String finalName =
                                    name;

                            final String finalCode =
                                    friendCode;

                            final boolean finalOnline =
                                    online;

                            eliteLevels.put(
                                    uid,
                                    EliteManager.loadProfile(uid).level()
                            );

                            if (this.minecraft != null) {

                                this.minecraft.execute(
                                        () ->
                                                friendProfiles.put(
                                                        uid,
                                                        new FriendProfile(
                                                                finalName,
                                                                finalCode,
                                                                finalOnline
                                                        )
                                                )
                                );
                            }
                        }
                );
            }

        } catch (Exception ignored) {

            friendProfiles.clear();
        }
    }

    private String displayName(
            String uid
    ) {

        if (uid == null
                || uid.isBlank()) {

            return "Player";
        }

        FriendProfile profile =
                friendProfiles.get(uid);

        if (profile != null
                && profile.name != null
                && !profile.name.isBlank()) {

            return profile.name;
        }

        String currentUid =
                WorldGateModClient
                        .FRIEND_MANAGER
                        .myUid();

        if (uid.equals(currentUid)) {

            return "You";
        }

        String roomName =
                roomPlayerName(uid);

        return roomName != null
                && !roomName.isBlank()
                ? roomName
                : "Player";
    }

    private String roomPlayerName(
            String uid
    ) {

        String json =
                roomJson;

        if (json == null
                || json.isBlank()
                || "null".equals(json)
                || uid == null) {

            return null;
        }

        try {

            JsonObject room =
                    JsonParser
                            .parseString(json)
                            .getAsJsonObject();

            if (!room.has("players")
                    || !room.get("players")
                            .isJsonObject()) {

                return null;
            }

            JsonObject players =
                    room.getAsJsonObject(
                            "players"
                    );

            if (!players.has(uid)
                    || !players.get(uid)
                            .isJsonObject()) {

                return null;
            }

            JsonObject player =
                    players.getAsJsonObject(uid);

            if (player.has("ign")) {

                String ign =
                        player.get("ign")
                                .getAsString();

                if (ign != null
                        && !ign.isBlank()) {

                    return ign;
                }
            }

        } catch (Exception ignored) {
        }

        return null;
    }

    private void sendChat() {

        String room =
                WorldGateModClient
                        .CURRENT_ROOM_CODE;

        if (room == null
                || room.isBlank()) {

            addChatMessage(
                    "Join or create a room to use chat."
            );

            return;
        }

        String text =
                chatBox
                        .getValue()
                        .trim();

        if (text.isEmpty()) {
            return;
        }

        chatBox.setValue("");

        WorldGateModClient.EXECUTOR.submit(
                () ->
                        WorldGateModClient
                                .CHAT_MANAGER
                                .sendMessage(
                                        room,
                                        text
                                )
        );
    }

    private void addChatMessage(
            String message
    ) {

        synchronized (chatMessages) {

            chatMessages.add(message);

            while (chatMessages.size() > 8) {

                chatMessages.remove(0);
            }
        }
    }

    private void goBack() {

        stopRealtime();

        if (minecraft != null) {

            minecraft.setScreen(parent);
        }
    }

    private void stopRealtime() {

        WorldGateModClient.FRIEND_MANAGER
                .stopRealtime();

        WorldGateModClient.ROOM_MANAGER
                .stopRealtime();

        WorldGateModClient.CHAT_MANAGER
                .stopListening();

        WorldGateModClient.EMOTE_MANAGER
                .stopListening();
    }
        @Override
    public void onClose() {

        goBack();
    }

    @Override
    public void removed() {

        stopRealtime();

        super.removed();
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

        int centerX =
                this.width / 2;

        int panelTop =
                84;

        int bottom =
                this.height - 58;

        graphics.centeredText(
                font,
                "WorldGate Lobby",
                centerX,
                16,
                0xFFFFFF
        );

        graphics.centeredText(font, "Friends • Players • Realtime Chat", centerX, 27, 0xAAAAAA);
        graphics.centeredText(font, status, centerX, 36, 0x8F9BA8);

        int leftX =
                12;

        int gap =
                8;

        int panelWidth =
                (this.width - 24 - gap * 2) / 3;

        int middleX =
                leftX + panelWidth + gap;

        int rightX =
                middleX + panelWidth + gap;

        drawPanel(
                graphics,
                leftX,
                panelTop,
                panelWidth,
                bottom - panelTop
        );

        drawPanel(
                graphics,
                middleX,
                panelTop,
                panelWidth,
                bottom - panelTop
        );

        drawPanel(
                graphics,
                rightX,
                panelTop,
                panelWidth,
                bottom - panelTop
        );

        graphics.text(
                font,
                "FRIENDS",
                leftX + 10,
                panelTop + 10,
                0x55FFFF
        );

        graphics.text(
                font,
                "IN ROOM",
                middleX + 10,
                panelTop + 10,
                0x55FF55
        );

        graphics.text(
                font,
                "CHAT",
                rightX + 10,
                panelTop + 10,
                0xFFAA55
        );

        drawFriends(
                graphics,
                leftX + 10,
                panelTop + 28,
                panelWidth - 20
        );

        drawRoomPlayers(
                graphics,
                middleX + 10,
                panelTop + 28,
                panelWidth - 20
        );

        drawChat(
                graphics,
                rightX + 10,
                panelTop + 28,
                panelWidth - 20
        );

        if (loading) {

            graphics.centeredText(
                    font,
                    "Loading...",
                    centerX,
                    panelTop + 2,
                    0xAAAAAA
            );
        }
    }

    private void drawPanel(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height
    ) {

        graphics.fill(
                x,
                y,
                x + width,
                y + height,
                0xAA111111
        );

        graphics.fill(
                x,
                y,
                x + width,
                y + 1,
                0xFF444444
        );
    }

    private void drawFriends(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width
    ) {

        if (friendsJson == null
                || friendsJson.equals("null")
                || friendsJson.isBlank()) {

            graphics.text(
                    font,
                    "No friends yet.",
                    x,
                    y,
                    0x888888
            );

            return;
        }

        try {

            JsonObject friends =
                    JsonParser
                            .parseString(friendsJson)
                            .getAsJsonObject();

            int row =
                    0;

            for (String uid :
                    friends.keySet()) {

                if (row >= 9) {
                    break;
                }

                FriendProfile profile =
                        friendProfiles.get(uid);

                String name =
                        profile != null
                                ? profile.name
                                : "Loading...";

                String code =
                        profile != null
                                ? profile.friendCode
                                : "--------";

                boolean online =
                        profile != null
                                && profile.online;

                int rowY =
                        y + row * 25;

                int eliteLevel =
                        eliteLevels.getOrDefault(uid, 0);

                if (eliteLevel == 0
                        && !eliteLevels.containsKey(uid)) {
                    eliteLevels.putIfAbsent(uid, -1);
                    WorldGateModClient.EXECUTOR.submit(
                            () -> eliteLevels.put(
                                    uid,
                                    EliteManager.loadProfile(uid).level()
                            )
                    );
                }

                if (eliteLevel > 0) {
                    EliteBadgeRenderer.draw(
                            graphics,
                            font,
                            x + 10,
                            rowY - 4,
                            22,
                            eliteLevel
                    );
                }

                graphics.text(
                        font,
                        name,
                        x + 27,
                        rowY,
                        0xFFFFFF
                );

                graphics.text(
                        font,
                        code,
                        x,
                        rowY + 10,
                        0x888888
                );

                String state =
                        online
                                ? "● Online"
                                : "○ Offline";

                graphics.text(
        font,
        state,
        x + width - font.width(state),
        rowY + 4,
        online
                ? 0x55FF55
                : 0x777777
);

                row++;
            }

        } catch (Exception ignored) {

            graphics.text(
                    font,
                    "Friends unavailable.",
                    x,
                    y,
                    0xAAAAAA
            );
        }
    }

    private void drawRoomPlayers(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width
    ) {

        String json =
                roomJson;

        if (json == null
                || json.equals("null")
                || json.isBlank()) {

            graphics.text(
                    font,
                    "No active room.",
                    x,
                    y,
                    0x888888
            );

            return;
        }

        try {

            JsonObject room =
                    JsonParser
                            .parseString(json)
                            .getAsJsonObject();

            if (!room.has("players")
                    || !room.get("players")
                            .isJsonObject()) {

                graphics.text(
                        font,
                        "No players.",
                        x,
                        y,
                        0x888888
                );

                return;
            }

            JsonObject players =
                    room.getAsJsonObject(
                            "players"
                    );

            int row =
                    0;

            for (String uid :
                    players.keySet()) {

                if (row >= 9) {
                    break;
                }

                JsonObject player =
                        players.getAsJsonObject(uid);

                String name =
                        null;

                if (player.has("ign")) {

                    name =
                            player.get("ign")
                                    .getAsString();
                }

                /*
                 * Never display a UID as the player name.
                 * Use IGN, friend profile name, or a generic
                 * Player label instead.
                 */
                if (name == null
                        || name.isBlank()) {

                    name =
                            displayName(uid);
                }

                if (name == null
                        || name.isBlank()) {

                    name =
                            "Player";
                }

                boolean online =
                        player.has("online")
                                && player.get("online")
                                        .getAsBoolean();

                int rowY =
                        y + row * 25;

                int eliteLevel =
                        eliteLevels.getOrDefault(uid, 0);

                if (eliteLevel == 0
                        && !eliteLevels.containsKey(uid)) {
                    eliteLevels.putIfAbsent(uid, -1);
                    WorldGateModClient.EXECUTOR.submit(
                            () -> eliteLevels.put(
                                    uid,
                                    EliteManager.loadProfile(uid).level()
                            )
                    );
                }

                if (eliteLevel > 0) {
                    EliteBadgeRenderer.draw(
                            graphics,
                            font,
                            x + 10,
                            rowY - 4,
                            22,
                            eliteLevel
                    );
                }

                graphics.text(
                        font,
                        name,
                        x + 27,
                        rowY,
                        0xFFFFFF
                );

                graphics.text(
                        font,
                        online
                                ? "● Online"
                                : "○ Offline",
                        x,
                        rowY + 10,
                        online
                                ? 0x55FF55
                                : 0x777777
                );

                row++;
            }

        } catch (Exception ignored) {

            graphics.text(
                    font,
                    "Players unavailable.",
                    x,
                    y,
                    0xAAAAAA
            );
        }
    }

    private void drawChat(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width
    ) {

        synchronized (chatMessages) {

            if (chatMessages.isEmpty()) {

                graphics.text(
                        font,
                        "No messages yet.",
                        x,
                        y,
                        0x888888
                );

                return;
            }

            int row =
                    Math.max(
                            0,
                            chatMessages.size() - 8
                    );

            int drawY =
                    y;

            while (
                    row < chatMessages.size()
            ) {

                String message =
                        chatMessages.get(row);

                graphics.text(
                        font,
                        message,
                        x,
                        drawY,
                        0xFFFFFF
                );

                drawY += 12;

                if (
                        drawY
                                > this.height - 72
                ) {

                    break;
                }

                row++;
            }
        }
    }
}
