package com.rcraja.worldgate.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rcraja.worldgate.client.WorldGateModClient;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class LobbyScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget chatBox;
    private final List<String> messages = new ArrayList<>();
    private volatile String friendsJson;
    private volatile String roomJson;

    public LobbyScreen(Screen parent) {
        super(Text.translatable("worldgate.lobby.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = width / 2;

        chatBox = new TextFieldWidget(
                textRenderer, 15, height - 32, width - 95, 20,
                Text.literal("Chat"));
        chatBox.setMaxLength(200);
        chatBox.setPlaceholder(Text.literal("Type a message..."));
        addDrawableChild(chatBox);

        addDrawableChild(ButtonWidget.builder(Text.literal("Send"), b -> sendChat())
                .dimensions(width - 75, height - 32, 60, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("worldgate.button.back"), b -> close())
                .dimensions(centerX - 70, height - 7, 140, 20).build());

        startRealtime();
    }

    private void startRealtime() {
        WorldGateModClient.FRIEND_MANAGER.setFriendListChangedListener(json -> {
            friendsJson = json;
            if (client != null) client.execute(this::refresh);
        });
        WorldGateModClient.FRIEND_MANAGER.startRealtime();

        String room = WorldGateModClient.CURRENT_ROOM_CODE;
        if (room != null && !room.isBlank()) {
            WorldGateModClient.ROOM_MANAGER.setRoomChangedListener(json -> {
                roomJson = json;
                if (client != null) client.execute(this::refresh);
            });
            WorldGateModClient.ROOM_MANAGER.startRealtime(room);

            WorldGateModClient.CHAT_MANAGER.listen(room, (uid, text) ->
                    client.execute(() ->
                            addMessage("<" + displayName(uid) + "> " + text)));
        }

        WorldGateModClient.EXECUTOR.submit(() -> {
            String json = WorldGateModClient.FRIEND_MANAGER.getFriends();
            if (client != null) client.execute(() -> {
                friendsJson = json;
                refresh();
            });
        });
    }

    private void refresh() {
        // Rendering reads the latest volatile JSON directly.
    }

    private void sendChat() {
        String room = WorldGateModClient.CURRENT_ROOM_CODE;
        if (room == null || room.isBlank()) {
            addMessage("Join or create a room to use chat.");
            return;
        }

        String text = chatBox.getText().trim();
        if (text.isEmpty()) return;

        chatBox.setText("");
        WorldGateModClient.EXECUTOR.submit(() ->
                WorldGateModClient.CHAT_MANAGER.sendMessage(room, text));
    }

    private void addMessage(String message) {
        synchronized (messages) {
            messages.add(message);
            while (messages.size() > 8) messages.remove(0);
        }
    }

    private String displayName(String uid) {
        if (uid == null || uid.isBlank()) return "Player";
        if (client != null && client.player != null &&
                uid.equals(WorldGateModClient.FRIEND_MANAGER.myUid())) {
            return client.player.getName().getString();
        }
        return "Player";
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        int panelTop = 28;
        int panelHeight = height - 80;
        int gap = 8;
        int panelWidth = (width - 24 - gap * 2) / 3;
        int left = 8;
        int middle = left + panelWidth + gap;
        int right = middle + panelWidth + gap;

        drawPanel(context, left, panelTop, panelWidth, panelHeight);
        drawPanel(context, middle, panelTop, panelWidth, panelHeight);
        drawPanel(context, right, panelTop, panelWidth, panelHeight);

        context.drawCenteredTextWithShadow(
                textRenderer, Text.translatable("worldgate.lobby.title"),
                width / 2, 10, 0xFFFFFF);

        context.drawTextWithShadow(textRenderer, Text.literal("FRIENDS"),
                left + 8, panelTop + 8, 0x55FFFF);
        context.drawTextWithShadow(textRenderer, Text.literal("IN ROOM"),
                middle + 8, panelTop + 8, 0x55FF55);
        context.drawTextWithShadow(textRenderer, Text.literal("CHAT"),
                right + 8, panelTop + 8, 0xFFAA55);

        drawFriends(context, left + 8, panelTop + 25);
        drawRoom(context, middle + 8, panelTop + 25);
        drawChat(context, right + 8, panelTop + 25);

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawPanel(DrawContext context, int x, int y, int w, int h) {
        context.fill(x, y, x + w, y + h, 0xAA111111);
        context.fill(x, y, x + w, y + 1, 0xFF444444);
    }

    private void drawFriends(DrawContext context, int x, int y) {
        if (friendsJson == null || friendsJson.isBlank() || "null".equals(friendsJson)) {
            context.drawTextWithShadow(textRenderer, Text.literal("No friends yet."), x, y, 0x888888);
            return;
        }

        try {
            JsonObject friends = JsonParser.parseString(friendsJson).getAsJsonObject();
            int row = 0;
            for (String uid : friends.keySet()) {
                if (row >= 9) break;
                String name = "Player";
                String code = "Friend";
                String profile = WorldGateModClient.FRIEND_MANAGER.getProfile(uid);

                try {
                    if (profile != null && !profile.equals("null")) {
                        JsonObject p = JsonParser.parseString(profile).getAsJsonObject();
                        if (p.has("displayName")) name = p.get("displayName").getAsString();
                        if (p.has("friendCode")) code = p.get("friendCode").getAsString();
                    }
                } catch (Exception ignored) {}

                context.drawTextWithShadow(textRenderer, Text.literal(name), x, y + row * 22, 0xFFFFFF);
                context.drawTextWithShadow(textRenderer, Text.literal(code), x, y + row * 22 + 10, 0x888888);
                row++;
            }
        } catch (Exception ignored) {}
    }

    private void drawRoom(DrawContext context, int x, int y) {
        if (roomJson == null || roomJson.isBlank() || "null".equals(roomJson)) {
            context.drawTextWithShadow(textRenderer, Text.literal("No active room."), x, y, 0x888888);
            return;
        }

        try {
            JsonObject room = JsonParser.parseString(roomJson).getAsJsonObject();
            if (!room.has("players") || !room.get("players").isJsonObject()) {
                context.drawTextWithShadow(textRenderer, Text.literal("No players."), x, y, 0x888888);
                return;
            }

            int row = 0;
            JsonObject players = room.getAsJsonObject("players");
            for (String uid : players.keySet()) {
                if (row >= 9) break;
                JsonObject player = players.getAsJsonObject(uid);
                String name = player.has("ign") ? player.get("ign").getAsString() : displayName(uid);
                boolean online = player.has("online") && player.get("online").getAsBoolean();

                context.drawTextWithShadow(textRenderer, Text.literal(name), x, y + row * 22, 0xFFFFFF);
                context.drawTextWithShadow(textRenderer,
                        Text.literal(online ? "● Online" : "○ Offline"),
                        x, y + row * 22 + 10, online ? 0x55FF55 : 0x777777);
                row++;
            }
        } catch (Exception ignored) {}
    }

    private void drawChat(DrawContext context, int x, int y) {
        synchronized (messages) {
            if (messages.isEmpty()) {
                context.drawTextWithShadow(textRenderer, Text.literal("No messages yet."), x, y, 0x888888);
                return;
            }

            int drawY = y;
            for (String message : messages) {
                context.drawTextWithShadow(textRenderer, Text.literal(message), x, drawY, 0xFFFFFF);
                drawY += 12;
                if (drawY > height - 55) break;
            }
        }
    }

    @Override
    public void close() {
        WorldGateModClient.FRIEND_MANAGER.stopRealtime();
        WorldGateModClient.ROOM_MANAGER.stopRealtime();
        WorldGateModClient.CHAT_MANAGER.stopListening();
        WorldGateModClient.EMOTE_MANAGER.stopListening();
        client.setScreen(parent);
    }
}
