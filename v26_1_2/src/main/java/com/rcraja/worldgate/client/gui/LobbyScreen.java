package com.rcraja.worldgate.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rcraja.worldgate.client.WorldGateModClient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LobbyScreen extends Screen {

    private final Screen parent;

    private EditBox chatBox;

    private final Map<String, FriendProfile> friendProfiles =
            new ConcurrentHashMap<>();

    private volatile String friendsJson = null;
    private volatile String roomJson = null;

    private final List<String> chatMessages =
            new ArrayList<>();

    private volatile boolean loading = true;

    public LobbyScreen(Screen parent) {
        super(Component.literal("WorldGate Lobby"));
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
                        Component.literal("Chat")
                );

        chatBox.setMaxLength(200);

        chatBox.setHint(
                Component.literal(
                        "Type a message..."
                )
        );

        addRenderableWidget(
                chatBox
        );

        addRenderableWidget(
                Button.builder(
                        Component.literal("Send"),
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
                Button.builder(
                        Component.literal("Back"),
                        btn -> goBack()
                )
                .bounds(
                        centerX - 70,
                        this.height - 20,
                        140,
                        20
                )
                .build()
        );

        startRealtime();
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

                    this.minecraft.execute(
                            () -> {

                                friendsJson = json;

                                refreshFriendProfiles();
                            }
                    );
                }
        );

        String room =
                WorldGateModClient
                        .CURRENT_ROOM_CODE;

        if (room != null
                && !room.isBlank()) {

            WorldGateModClient.ROOM_MANAGER
                    .setRoomChangedListener(
                            json -> {

                                roomJson = json;
                            }
                    );

            WorldGateModClient.ROOM_MANAGER
                    .startRealtime(room);

            WorldGateModClient.CHAT_MANAGER.listen(
                    room,
                    (uid, text) ->
                            this.minecraft.execute(
                                    () -> {

                                        String name =
                                                displayName(uid);

                                        addChatMessage(
                                                "<"
                                                        + name
                                                        + "> "
                                                        + text
                                        );
                                    }
                            )
            );
        }

        loading = false;
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
                                    "Unknown";

                            String friendCode =
                                    "WG------";

                            boolean online =
                                    false;

                            try {

                                if (profile != null
                                        && !profile.equals(
                                                "null"
                                        )
                                        && !profile.isBlank()) {

                                    JsonObject object =
                                            JsonParser
                                                    .parseString(
                                                            profile
                                                    )
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
                );
            }

        } catch (Exception ignored) {

            friendProfiles.clear();
        }
    }

    private String displayName(
            String uid
    ) {

        if (uid == null) {
            return "?";
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

        return roomPlayerName(uid) != null ? roomPlayerName(uid) : "Player";
    }

    private static String shortUid(
            String uid
    ) {

        if (uid == null
                || uid.isBlank()) {

            return "?";
        }

        return uid.substring(
                0,
                Math.min(
                        6,
                        uid.length()
                )
        );
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
    public void render(
            GuiGraphics graphics,
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

        int centerX =
                this.width / 2;

        int panelTop =
                38;

        int bottom =
                this.height - 58;

        /*
         * Header
         */
        graphics.drawCenteredString(
                font,
                "WorldGate Lobby",
                centerX,
                16,
                0xFFFFFF
        );

        graphics.drawCenteredString(
                font,
                "Friends • Players • Realtime Chat",
                centerX,
                27,
                0xAAAAAA
        );

        /*
         * Three main panels.
         */
        int leftX = 12;
        int gap = 8;
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

        /*
         * Panel titles.
         */
        graphics.drawString(
                font,
                "FRIENDS",
                leftX + 10,
                panelTop + 10,
                0x55FFFF
        );

        graphics.drawString(
                font,
                "IN ROOM",
                middleX + 10,
                panelTop + 10,
                0x55FF55
        );

        graphics.drawString(
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

            graphics.drawCenteredString(
                    font,
                    "Loading...",
                    centerX,
                    panelTop + 2,
                    0xAAAAAA
            );
        }

        super.render(
                graphics,
                mouseX,
                mouseY,
                delta
        );
    }

    private void drawPanel(
            GuiGraphics graphics,
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
            GuiGraphics graphics,
            int x,
            int y,
            int width
    ) {

        if (friendsJson == null
                || friendsJson.equals("null")
                || friendsJson.isBlank()) {

            graphics.drawString(
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

            int row = 0;

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

                graphics.drawString(
                        font,
                        name,
                        x,
                        rowY,
                        0xFFFFFF
                );

                graphics.drawString(
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

                graphics.drawRightAlignedString(
                        font,
                        state,
                        x + width,
                        rowY + 4,
                        online
                                ? 0x55FF55
                                : 0x777777
                );

                row++;
            }

        } catch (Exception ignored) {

            graphics.drawString(
                    font,
                    "Friends unavailable.",
                    x,
                    y,
                    0xAAAAAA
            );
        }
    }

    private void drawRoomPlayers(
            GuiGraphics graphics,
            int x,
            int y,
            int width
    ) {

        String json =
                roomJson;

        if (json == null
                || json.equals("null")
                || json.isBlank()) {

            graphics.drawString(
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
                    || !room.get(
                            "players"
                    ).isJsonObject()) {

                graphics.drawString(
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

            int row = 0;

            for (String uid :
                    players.keySet()) {

                if (row >= 9) {
                    break;
                }

                JsonObject player =
                        players.getAsJsonObject(
                                uid
                        );

                String name =
                        player.has("ign")
                                ? player.get(
                                        "ign"
                                ).getAsString()
                                : shortUid(uid);

                boolean online =
                        player.has("online")
                                && player.get(
                                        "online"
                                ).getAsBoolean();

                int rowY =
                        y + row * 25;

                graphics.drawString(
                        font,
                        name,
                        x,
                        rowY,
                        0xFFFFFF
                );

                graphics.drawString(
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

            graphics.drawString(
                    font,
                    "Players unavailable.",
                    x,
                    y,
                    0xAAAAAA
            );
        }
    }

    private void drawChat(
            GuiGraphics graphics,
            int x,
            int y,
            int width
    ) {

        synchronized (chatMessages) {

            if (chatMessages.isEmpty()) {

                graphics.drawString(
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

            int drawY = y;

            while (row < chatMessages.size()) {

                String message =
                        chatMessages.get(row);

                graphics.drawString(
                        font,
                        message,
                        x,
                        drawY,
                        0xFFFFFF
                );

                drawY += 12;

                if (drawY >
                        this.height - 72) {
                    break;
                }

                row++;
            }
        }
    }
}
