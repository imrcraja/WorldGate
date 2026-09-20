package com.rcraja.worldgate.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rcraja.worldgate.client.WorldGateModClient;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class FriendsScreen extends Screen {

    private final Screen parent;
    private TextFieldWidget friendCodeBox;

    private String myCode = "...";
    private String status = "";

    private final List<String> friendLines = new ArrayList<>();
    private final List<String> requestLines = new ArrayList<>();

    public FriendsScreen(Screen parent) {
        super(Text.translatable("worldgate.friends.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = width / 2;

        friendCodeBox = new TextFieldWidget(
                textRenderer, centerX - 100, 58, 200, 20,
                Text.literal("Friend Code"));
        friendCodeBox.setMaxLength(12);
        friendCodeBox.setPlaceholder(Text.literal("Enter Friend Code"));
        addDrawableChild(friendCodeBox);

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("worldgate.friends.send"),
                b -> sendRequest())
                .dimensions(centerX - 100, 83, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("worldgate.friends.accept"),
                b -> acceptSelected())
                .dimensions(centerX - 100, 108, 97, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Reject"),
                b -> rejectSelected())
                .dimensions(centerX + 3, 108, 97, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("worldgate.friends.refresh"),
                b -> loadAll())
                .dimensions(centerX - 100, 133, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("worldgate.button.back"),
                b -> close())
                .dimensions(centerX - 100, height - 28, 200, 20).build());

        loadProfile();

        WorldGateModClient.FRIEND_MANAGER.setFriendListChangedListener(
                ignored -> {
                    if (client != null) client.execute(this::loadAll);
                });

        WorldGateModClient.FRIEND_MANAGER.startRealtime();
        loadAll();
    }

    private void loadProfile() {
        WorldGateModClient.EXECUTOR.submit(() -> {
            String code = WorldGateModClient.FRIEND_MANAGER.myFriendCode();
            if (client != null) {
                client.execute(() -> myCode = code == null ? "..." : code);
            }
        });
    }

    private void sendRequest() {
        String code = friendCodeBox.getText().trim().toUpperCase();
        if (code.isEmpty()) {
            status = "Enter a Friend Code.";
            return;
        }

        status = "Sending request...";

        WorldGateModClient.EXECUTOR.submit(() -> {
            boolean ok = WorldGateModClient.FRIEND_MANAGER.sendRequest(code);
            if (client != null) {
                client.execute(() -> {
                    status = ok ? "Friend request sent." : "Could not send request.";
                    loadAll();
                });
            }
        });
    }

    private String selectedCode() {
        String code = friendCodeBox.getText().trim().toUpperCase();
        return code.isEmpty() ? null : code;
    }

    private void acceptSelected() {
        String code = selectedCode();
        if (code == null) {
            status = "Enter the request sender's Friend Code.";
            return;
        }

        WorldGateModClient.EXECUTOR.submit(() -> {
            boolean ok = WorldGateModClient.FRIEND_MANAGER.acceptRequest(code);
            if (client != null) {
                client.execute(() -> {
                    status = ok ? "Friend request accepted." : "Could not accept request.";
                    loadAll();
                });
            }
        });
    }

    private void rejectSelected() {
        String code = selectedCode();
        if (code == null) {
            status = "Enter the request sender's Friend Code.";
            return;
        }

        WorldGateModClient.EXECUTOR.submit(() -> {
            boolean ok = WorldGateModClient.FRIEND_MANAGER.rejectRequest(code);
            if (client != null) {
                client.execute(() -> {
                    status = ok ? "Friend request rejected." : "Could not reject request.";
                    loadAll();
                });
            }
        });
    }

    private void loadAll() {
        WorldGateModClient.EXECUTOR.submit(() -> {
            String friends = WorldGateModClient.FRIEND_MANAGER.getFriends();
            String requests = WorldGateModClient.FRIEND_MANAGER.getIncomingRequests();

            if (client != null) {
                client.execute(() -> {
                    friendLines.clear();
                    requestLines.clear();

                    parseFriends(friends);
                    parseRequests(requests);
                });
            }
        });
    }

    private void parseFriends(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) return;

        try {
            JsonObject friends = JsonParser.parseString(json).getAsJsonObject();

            for (String uid : friends.keySet()) {
                String name = "Player";
                String code = "Unknown";
                boolean online = false;

                String profile = WorldGateModClient.FRIEND_MANAGER.getProfile(uid);

                try {
                    if (profile != null && !profile.isBlank() && !"null".equals(profile)) {
                        JsonObject p = JsonParser.parseString(profile).getAsJsonObject();

                        if (p.has("displayName")) name = p.get("displayName").getAsString();
                        if (p.has("friendCode")) code = p.get("friendCode").getAsString();
                        if (p.has("online")) online = p.get("online").getAsBoolean();
                    }
                } catch (Exception ignored) {
                }

                friendLines.add(
                        name + "  [" + code + "]  " +
                        (online ? "Online" : "Offline"));
            }
        } catch (Exception ignored) {
        }
    }

    private void parseRequests(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) return;

        try {
            JsonObject requests = JsonParser.parseString(json).getAsJsonObject();

            for (String uid : requests.keySet()) {
                JsonObject request = requests.getAsJsonObject(uid);

                String name = request.has("fromName")
                        ? request.get("fromName").getAsString()
                        : "Player";

                String code = request.has("fromFriendCode")
                        ? request.get("fromFriendCode").getAsString()
                        : "Unknown";

                requestLines.add(name + "  [" + code + "]");
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context,
                       int mouseX, int mouseY, float delta) {
        renderBackground(context);

        int centerX = width / 2;

        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("worldgate.friends.title"),
                centerX, 15, 0xFFFFFF);

        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("Your Friend Code: " + myCode),
                centerX, 32, 0x55FFFF);

        context.drawTextWithShadow(
                textRenderer,
                Text.literal("FRIENDS"),
                25, 175, 0x55FF55);

        int y = 190;
        for (String line : friendLines) {
            if (y > height - 100) break;
            context.drawTextWithShadow(
                    textRenderer, Text.literal(line), 25, y, 0xFFFFFF);
            y += 16;
        }

        context.drawTextWithShadow(
                textRenderer,
                Text.literal("INCOMING REQUESTS"),
                width / 2 + 10, 175, 0xFFAA55);

        y = 190;
        for (String line : requestLines) {
            if (y > height - 100) break;
            context.drawTextWithShadow(
                    textRenderer, Text.literal(line), width / 2 + 10, y, 0xFFFFFF);
            y += 16;
        }

        if (!status.isEmpty()) {
            context.drawCenteredTextWithShadow(
                    textRenderer, Text.literal(status),
                    centerX, height - 48, 0xFFFF55);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        WorldGateModClient.FRIEND_MANAGER.stopRealtime();
        client.setScreen(parent);
    }
}
