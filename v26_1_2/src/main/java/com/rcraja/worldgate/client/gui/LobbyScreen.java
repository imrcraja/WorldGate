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

    private final Map<String, String> friendNames =
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

    @Override
    protected void init() {
        int centerX = this.width / 2;

        chatBox = new EditBox(
                this.font,
                centerX - 100,
                this.height - 55,
                175,
                20,
                Component.literal("Chat")
        );

        chatBox.setMaxLength(200);
        chatBox.setHint(Component.literal("Type a message..."));
        addRenderableWidget(chatBox);

        addRenderableWidget(
                Button.builder(
                        Component.literal("Send"),
                        btn -> sendChat()
                )
                .bounds(
                        centerX + 80,
                        this.height - 55,
                        45,
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
                        centerX - 100,
                        this.height - 28,
                        200,
                        20
                )
                .build()
        );

        startRealtime();
    }

    private void startRealtime() {
        loading = true;

        WorldGateModClient.FRIEND_MANAGER
                .setFriendListChangedListener(json -> {
                    friendsJson = json;
                    refreshFriendNames();
                });

        WorldGateModClient.FRIEND_MANAGER.startRealtime();

        WorldGateModClient.EXECUTOR.submit(() -> {
            String json =
                    WorldGateModClient.FRIEND_MANAGER.getFriends();

            this.minecraft.execute(() -> {
                friendsJson = json;
                refreshFriendNames();
            });
        });

        String room =
                WorldGateModClient.CURRENT_ROOM_CODE;

        if (room != null && !room.isBlank()) {
            WorldGateModClient.ROOM_MANAGER
                    .setRoomChangedListener(json -> {
                        roomJson = json;
                        this.minecraft.execute(
                                this::refreshFriendNames
                        );
                    });

            WorldGateModClient.ROOM_MANAGER
                    .startRealtime(room);

            WorldGateModClient.CHAT_MANAGER.listen(
                    room,
                    (uid, text) -> this.minecraft.execute(() -> {
                        String name = displayName(uid);
                        addChatMessage(
                                "<" + name + "> " + text
                        );
                    })
            );

            loading = false;
        } else {
            loading = false;
        }
    }

    private void refreshFriendNames() {
        String json = friendsJson;

        if (json == null
                || json.equals("null")) {
            return;
        }

        try {
            JsonObject friends =
                    JsonParser.parseString(json)
                            .getAsJsonObject();

            for (String uid : friends.keySet()) {
                if (friendNames.containsKey(uid)) {
                    continue;
                }

                WorldGateModClient.EXECUTOR.submit(() -> {
                    String profile =
                            WorldGateModClient.FRIEND_MANAGER
                                    .getProfile(uid);

                    String name = uid;

                    try {
                        if (profile != null
                                && !profile.equals("null")) {
                            JsonObject object =
                                    JsonParser.parseString(profile)
                                            .getAsJsonObject();

                            if (object.has("displayName")) {
                                name = object
                                        .get("displayName")
                                        .getAsString();
                            }
                        }
                    } catch (Exception ignored) {
                    }

                    final String finalName = name;

                    this.minecraft.execute(() ->
                            friendNames.put(uid, finalName)
                    );
                });
            }
        } catch (Exception ignored) {
        }
    }

    private String displayName(String uid) {
        if (uid == null) {
            return "?";
        }

        String name = friendNames.get(uid);

        if (name != null && !name.isBlank()) {
            return name;
        }

        String currentUid =
                WorldGateModClient.FRIEND_MANAGER.myUid();

        if (uid.equals(currentUid)) {
            return "You";
        }

        return shortUid(uid);
    }

    private static String shortUid(String uid) {
        return uid.substring(
                0,
                Math.min(6, uid.length())
        );
    }

    private void sendChat() {
        String room =
                WorldGateModClient.CURRENT_ROOM_CODE;

        if (room == null || room.isBlank()) {
            addChatMessage("Join or create a room to use chat.");
            return;
        }

        String text =
                chatBox.getValue().trim();

        if (text.isEmpty()) {
            return;
        }

        chatBox.setValue("");

        WorldGateModClient.EXECUTOR.submit(() ->
                WorldGateModClient.CHAT_MANAGER
                        .sendMessage(room, text)
        );
    }

    private void addChatMessage(String message) {
        synchronized (chatMessages) {
            chatMessages.add(message);

            while (chatMessages.size() > 6) {
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

        int centerX = this.width / 2;

        graphics.drawCenteredString(
                font,
                "WorldGate Lobby",
                centerX,
                18,
                0xFFFFFF
        );

        graphics.drawString(
                font,
                "Friends",
                20,
                42,
                0x55FFFF
        );

        graphics.drawString(
                font,
                "Players in Room",
                this.width - 155,
                42,
                0x55FFFF
        );

        drawFriends(
                graphics,
                20,
                58
        );

        drawRoomPlayers(
                graphics,
                this.width - 155,
                58
        );

        graphics.drawString(
                font,
                "Realtime Chat",
                20,
                this.height - 78,
                0x55FFFF
        );

        synchronized (chatMessages) {
            int y = this.height - 68;

            for (String message : chatMessages) {
                graphics.drawString(
                        font,
                        message,
                        20,
                        y,
                        0xFFFFFF
                );

                y += 10;
            }
        }

        if (loading) {
            graphics.drawCenteredString(
                    font,
                    "Loading...",
                    centerX,
                    35,
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

    private void drawFriends(
            GuiGraphics graphics,
            int x,
            int y
    ) {
        if (friendsJson == null
                || friendsJson.equals("null")) {
            graphics.drawString(
                    font,
                    "No friends yet.",
                    x,
                    y,
                    0xAAAAAA
            );
            return;
        }

        try {
            JsonObject friends =
                    JsonParser.parseString(friendsJson)
                            .getAsJsonObject();

            int row = 0;

            for (String uid : friends.keySet()) {
                if (row >= 9) break;

                String name = displayName(uid);

                graphics.drawString(
                        font,
                        name,
                        x,
                        y + row * 12,
                        0xFFFFFF
                );

                graphics.drawString(
                        font,
                        shortUid(uid),
                        x + 95,
                        y + row * 12,
                        0xAAAAAA
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
            int y
    ) {
        String json = roomJson;

        if (json == null
                || json.equals("null")) {
            graphics.drawString(
                    font,
                    "No active room.",
                    x,
                    y,
                    0xAAAAAA
            );
            return;
        }

        try {
            JsonObject room =
                    JsonParser.parseString(json)
                            .getAsJsonObject();

            if (!room.has("players")
                    || !room.get("players").isJsonObject()) {
                graphics.drawString(
                        font,
                        "No players.",
                        x,
                        y,
                        0xAAAAAA
                );
                return;
            }

            JsonObject players =
                    room.getAsJsonObject("players");

            int row = 0;

            for (String uid : players.keySet()) {
                if (row >= 9) break;

                JsonObject player =
                        players.getAsJsonObject(uid);

                String name =
                        player.has("ign")
                                ? player.get("ign").getAsString()
                                : shortUid(uid);

                boolean online =
                        player.has("online")
                                && player.get("online")
                                        .getAsBoolean();

                String status =
                        online ? "● Online" : "○ Offline";

                graphics.drawString(
                        font,
                        name,
                        x,
                        y + row * 12,
                        0xFFFFFF
                );

                graphics.drawString(
                        font,
                        status,
                        x + 70,
                        y + row * 12,
                        online
                                ? 0x55FF55
                                : 0xAAAAAA
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
}
