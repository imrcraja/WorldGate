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

    /*
     * uid -> profile information
     */
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
        chatBox.setHint(
                Component.literal("Type a message...")
        );

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

        /*
         * Friend list realtime update.
         */
        WorldGateModClient.FRIEND_MANAGER
                .setFriendListChangedListener(json -> {

                    friendsJson = json;

                    this.minecraft.execute(
                            this::refreshFriendProfiles
                    );
                });

        WorldGateModClient.FRIEND_MANAGER
                .startRealtime();

        /*
         * Initial friend list.
         */
        WorldGateModClient.EXECUTOR.submit(() -> {

            String json =
                    WorldGateModClient.FRIEND_MANAGER
                            .getFriends();

            this.minecraft.execute(() -> {

                friendsJson = json;

                refreshFriendProfiles();
            });
        });

        String room =
                WorldGateModClient.CURRENT_ROOM_CODE;

        if (room != null && !room.isBlank()) {

            /*
             * Room realtime update.
             */
            WorldGateModClient.ROOM_MANAGER
                    .setRoomChangedListener(json -> {

                        roomJson = json;

                        this.minecraft.execute(
                                this::refreshFriendProfiles
                        );
                    });

            WorldGateModClient.ROOM_MANAGER
                    .startRealtime(room);

            /*
             * Realtime chat.
             */
            WorldGateModClient.CHAT_MANAGER.listen(
                    room,
                    (uid, text) ->
                            this.minecraft.execute(() -> {

                                String name =
                                        displayName(uid);

                                addChatMessage(
                                        "<" + name + "> " + text
                                );
                            })
            );
        }

        loading = false;
    }

    /*
     * Load/update all friend profiles.
     */
    private void refreshFriendProfiles() {

        String json = friendsJson;

        if (json == null
                || json.equals("null")
                || json.isBlank()) {
            return;
        }

        try {

            JsonObject friends =
                    JsonParser.parseString(json)
                            .getAsJsonObject();

            for (String uid : friends.keySet()) {

                /*
                 * Already loaded profile.
                 * It will still be refreshed when the
                 * friend list itself changes.
                 */
                WorldGateModClient.EXECUTOR.submit(() -> {

                    String profile =
                            WorldGateModClient.FRIEND_MANAGER
                                    .getProfile(uid);

                    String name =
                            "Unknown";

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

                            if (object.has("displayName")) {

                                name =
                                        object.get(
                                                "displayName"
                                        ).getAsString();
                            }

                            if (object.has("friendCode")) {

                                friendCode =
                                        object.get(
                                                "friendCode"
                                        ).getAsString();
                            }

                            if (object.has("online")) {

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

                    this.minecraft.execute(() -> {

                        friendProfiles.put(
                                uid,
                                new FriendProfile(
                                        finalName,
                                        finalCode,
                                        finalOnline
                                )
                        );
                    });
                });
            }

        } catch (Exception ignored) {
        }
    }

    private String displayName(String uid) {

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
                WorldGateModClient.FRIEND_MANAGER
                        .myUid();

        if (uid.equals(currentUid)) {
            return "You";
        }

        return shortUid(uid);
    }

    private static String shortUid(String uid) {

        if (uid == null || uid.isBlank()) {
            return "?";
        }

        return uid.substring(
                0,
                Math.min(6, uid.length())
        );
    }

    private void sendChat() {

        String room =
                WorldGateModClient.CURRENT_ROOM_CODE;

        if (room == null || room.isBlank()) {

            addChatMessage(
                    "Join or create a room to use chat."
            );

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

        int centerX =
                this.width / 2;

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

            int y =
                    this.height - 68;

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

                if (row >= 9) {
                    break;
                }

                FriendProfile profile =
                        friendProfiles.get(uid);

                String name;

                String code;

                boolean online;

                if (profile != null) {

                    name =
                            profile.name;

                    code =
                            profile.friendCode;

                    online =
                            profile.online;

                } else {

                    name =
                            "Loading...";

                    code =
                            "--------";

                    online =
                            false;
                }

                /*
                 * Name
                 */
                graphics.drawString(
                        font,
                        name,
                        x,
                        y + row * 12,
                        0xFFFFFF
                );

                /*
                 * Friend Code
                 */
                graphics.drawString(
                        font,
                        code,
                        x + 85,
                        y + row * 12,
                        0xAAAAAA
                );

                /*
                 * Online / Offline
                 */
                graphics.drawString(
                        font,
                        online
                                ? "● Online"
                                : "○ Offline",
                        x + 165,
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

        String json =
                roomJson;

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
                    || !room.get("players")
                            .isJsonObject()) {

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
                    room.getAsJsonObject(
                            "players"
                    );

            int row = 0;

            for (String uid : players.keySet()) {

                if (row >= 9) {
                    break;
                }

                JsonObject player =
                        players.getAsJsonObject(uid);

                String name =
                        player.has("ign")
                                ? player.get("ign")
                                        .getAsString()
                                : shortUid(uid);

                boolean online =
                        player.has("online")
                                && player.get("online")
                                        .getAsBoolean();

                String status =
                        online
                                ? "● Online"
                                : "○ Offline";

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
