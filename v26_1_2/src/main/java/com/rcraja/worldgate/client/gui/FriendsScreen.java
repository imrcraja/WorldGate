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

public class FriendsScreen extends Screen {

    private final Screen parent;

    private EditBox friendCodeBox;

    private final List<FriendEntry> friends =
            new ArrayList<>();

    private String myCode = "...";
    private String status = "";

    public FriendsScreen(Screen parent) {
        super(Component.translatable(
                "worldgate.friends.title"
        ));

        this.parent = parent;
    }

    @Override
    protected void init() {

        int centerX = this.width / 2;

        friendCodeBox =
                new EditBox(
                        this.font,
                        centerX - 100,
                        65,
                        200,
                        20,
                        Component.literal("Friend Code")
                );

        friendCodeBox.setMaxLength(12);
        friendCodeBox.setHint(
                Component.literal("Enter Friend Code")
        );

        this.addRenderableWidget(friendCodeBox);

        this.addRenderableWidget(
                Button.builder(
                        Component.literal("Add Friend"),
                        btn -> sendRequest()
                )
                .bounds(
                        centerX - 100,
                        90,
                        200,
                        20
                )
                .build()
        );

        this.addRenderableWidget(
                Button.builder(
                        Component.literal("Back"),
                        btn -> this.minecraft.setScreen(parent)
                )
                .bounds(
                        centerX - 100,
                        this.height - 30,
                        200,
                        20
                )
                .build()
        );

        loadProfile();

        WorldGateModClient.FRIEND_MANAGER
                .setFriendListChangedListener(
                        ignored ->
                                this.minecraft.execute(
                                        this::loadFriends
                                )
                );

        WorldGateModClient.FRIEND_MANAGER
                .startRealtime();

        loadFriends();
    }

    private void loadProfile() {

        WorldGateModClient.EXECUTOR.submit(() -> {

            String code =
                    WorldGateModClient.FRIEND_MANAGER
                            .myFriendCode();

            String name =
                    this.minecraft.player == null
                            ? "Player"
                            : this.minecraft.player
                                    .getName()
                                    .getString();

            WorldGateModClient.FRIEND_MANAGER
                    .setOnline(name);

            this.minecraft.execute(() -> {

                myCode =
                        code == null
                                ? "..."
                                : code;

                status =
                        "Your Friend Code: " + myCode;

                loadFriends();
            });
        });
    }

    private void sendRequest() {

        String code =
                friendCodeBox.getValue()
                        .trim()
                        .toUpperCase();

        if (code.isEmpty()) {
            status = "Enter a Friend Code.";
            return;
        }

        status = "Sending request...";

        WorldGateModClient.EXECUTOR.submit(() -> {

            boolean ok =
                    WorldGateModClient.FRIEND_MANAGER
                            .sendRequestByCode(code);

            this.minecraft.execute(() ->
                    status =
                            ok
                                    ? "Friend request sent."
                                    : "Friend Code not found."
            );
        });
    }

    private void loadFriends() {

        WorldGateModClient.EXECUTOR.submit(() -> {

            String json =
                    WorldGateModClient.FRIEND_MANAGER
                            .getFriends();

            List<FriendEntry> result =
                    new ArrayList<>();

            if (json != null
                    && !json.equals("null")) {

                try {

                    JsonObject object =
                            JsonParser.parseString(json)
                                    .getAsJsonObject();

                    for (String uid :
                            object.keySet()) {

                        String profile =
                                WorldGateModClient.FRIEND_MANAGER
                                        .getProfile(uid);

                        if (profile == null
                                || profile.equals("null")) {
                            continue;
                        }

                        JsonObject p =
                                JsonParser.parseString(profile)
                                        .getAsJsonObject();

                        String name =
                                p.has("displayName")
                                        ? p.get(
                                                "displayName"
                                        ).getAsString()
                                        : "Player";

                        String code =
                                p.has("friendCode")
                                        ? p.get(
                                                "friendCode"
                                        ).getAsString()
                                        : "--------";

                        boolean online =
                                p.has("online")
                                        && p.get("online")
                                                .getAsBoolean();

                        result.add(
                                new FriendEntry(
                                        uid,
                                        name,
                                        code,
                                        online
                                )
                        );
                    }

                } catch (Exception ignored) {
                }
            }

            this.minecraft.execute(() -> {

                friends.clear();
                friends.addAll(result);
            });
        });
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float delta
    ) {

        this.renderBackground(
                graphics,
                mouseX,
                mouseY,
                delta
        );

        int centerX = this.width / 2;

        graphics.drawCenteredString(
                this.font,
                "WorldGate Friends",
                centerX,
                25,
                0xFFFFFF
        );

        graphics.drawCenteredString(
                this.font,
                status,
                centerX,
                45,
                0xAAAAAA
        );

        int y = 125;

        if (friends.isEmpty()) {

            graphics.drawCenteredString(
                    this.font,
                    "No friends yet.",
                    centerX,
                    y,
                    0x888888
            );

        } else {

            for (FriendEntry friend : friends) {

                String state =
                        friend.online
                                ? "● Online"
                                : "○ Offline";

                graphics.drawString(
                        this.font,
                        friend.name,
                        centerX - 100,
                        y,
                        0xFFFFFF
                );

                graphics.drawString(
                        this.font,
                        friend.code,
                        centerX - 100,
                        y + 12,
                        0xAAAAAA
                );

                graphics.drawRightAlignedString(
                        this.font,
                        state,
                        centerX + 100,
                        y + 6,
                        friend.online
                                ? 0x55FF55
                                : 0x888888
                );

                y += 35;
            }
        }

        super.render(
                graphics,
                mouseX,
                mouseY,
                delta
        );
    }

    @Override
    public void onClose() {

        WorldGateModClient.FRIEND_MANAGER
                .setFriendListChangedListener(null);

        WorldGateModClient.FRIEND_MANAGER
                .stopRealtime();

        WorldGateModClient.FRIEND_MANAGER
                .setOffline();

        this.minecraft.setScreen(parent);
    }

    private record FriendEntry(
            String uid,
            String name,
            String code,
            boolean online
    ) {
    }
}
