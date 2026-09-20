package com.rcraja.worldgate.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rcraja.worldgate.client.WorldGateModClient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FriendsScreen extends Screen {

    private final Screen parent;

    private EditBox friendCodeBox;

    private final List<FriendEntry> friends =
            new ArrayList<>();

    private final List<RequestEntry> requests =
            new ArrayList<>();

    private String selectedRequestUid = null;

    private String myCode = "...";
    private String status = "";

    private final Set<String> knownRequestUids =
            new HashSet<>();

    private boolean requestSnapshotReady = false;

    private String requestNotification = "";

    private long requestNotificationUntil = 0L;

    public FriendsScreen(Screen parent) {

        super(
                Component.translatable(
                        "worldgate.friends.title"
                )
        );

        this.parent = parent;
    }

    @Override
    protected void init() {

        int centerX =
                this.width / 2;

        friendCodeBox =
                new EditBox(
                        this.font,
                        centerX - 100,
                        60,
                        200,
                        20,
                        Component.literal(
                                "Friend Code"
                        )
                );

        friendCodeBox.setMaxLength(12);

        friendCodeBox.setHint(
                Component.literal(
                        "Enter Friend Code"
                )
        );

        this.addRenderableWidget(
                friendCodeBox
        );

        this.addRenderableWidget(
                Button.builder(
                        Component.literal(
                                "Add Friend"
                        ),
                        btn -> sendRequest()
                )
                .bounds(
                        centerX - 100,
                        85,
                        200,
                        20
                )
                .build()
        );

        this.addRenderableWidget(
                Button.builder(
                        Component.literal(
                                "Accept"
                        ),
                        btn -> acceptSelectedRequest()
                )
                .bounds(
                        centerX - 100,
                        110,
                        97,
                        20
                )
                .build()
        );

        this.addRenderableWidget(
                Button.builder(
                        Component.literal(
                                "Reject"
                        ),
                        btn -> rejectSelectedRequest()
                )
                .bounds(
                        centerX + 3,
                        110,
                        97,
                        20
                )
                .build()
        );

        this.addRenderableWidget(
                Button.builder(
                        Component.literal(
                                "Back"
                        ),
                        btn -> closeScreen()
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
                        ignored -> {

                            if (this.minecraft != null) {

                                this.minecraft.execute(
                                        this::loadAll
                                );
                            }
                        }
                );

        WorldGateModClient.FRIEND_MANAGER
                .startRealtime();

        loadAll();
    }

    private void loadProfile() {

        WorldGateModClient.EXECUTOR.submit(
                () -> {

                    String code =
                            WorldGateModClient
                                    .FRIEND_MANAGER
                                    .myFriendCode();

                    String name =
                            this.minecraft
                                    .getUser()
                                    .getName();

                    WorldGateModClient
                            .FRIEND_MANAGER
                            .setOnline(name);

                    this.minecraft.execute(
                            () -> {

                                myCode =
                                        code == null
                                                ? "..."
                                                : code;

                                status =
                                        "Your Friend Code: "
                                                + myCode;
                            }
                    );
                }
        );
    }

    private void sendRequest() {

        String code =
                friendCodeBox
                        .getValue()
                        .trim()
                        .toUpperCase();

        if (code.isEmpty()) {

            status =
                    "Enter a Friend Code.";

            return;
        }

        status =
                "Sending request...";

        WorldGateModClient.EXECUTOR.submit(
                () -> {

                    boolean ok =
                            WorldGateModClient
                                    .FRIEND_MANAGER
                                    .sendRequestByCode(
                                            code
                                    );

                    this.minecraft.execute(
                            () -> {

                                status =
                                        ok
                                                ? "Friend request sent."
                                                : "Friend Code not found.";
                            }
                    );
                }
        );
    }

    private void loadAll() {

        loadFriends();

        loadRequests();
    }

    private void loadFriends() {

        WorldGateModClient.EXECUTOR.submit(
                () -> {

                    String json =
                            WorldGateModClient
                                    .FRIEND_MANAGER
                                    .getFriends();

                    List<FriendEntry> result =
                            new ArrayList<>();

                    if (json != null
                            && !json.equals("null")) {

                        try {

                            JsonObject object =
                                    JsonParser
                                            .parseString(json)
                                            .getAsJsonObject();

                            for (String uid :
                                    object.keySet()) {

                                String profile =
                                        WorldGateModClient
                                                .FRIEND_MANAGER
                                                .getProfile(uid);

                                if (profile == null
                                        || profile.equals(
                                                "null"
                                        )) {

                                    continue;
                                }

                                JsonObject p =
                                        JsonParser
                                                .parseString(
                                                        profile
                                                )
                                                .getAsJsonObject();

                                String name =
                                        p.has(
                                                "displayName"
                                        )
                                                ? p.get(
                                                        "displayName"
                                                ).getAsString()
                                                : "Player";

                                String code =
                                        p.has(
                                                "friendCode"
                                        )
                                                ? p.get(
                                                        "friendCode"
                                                ).getAsString()
                                                : "--------";

                                boolean online =
                                        p.has("online")
                                                && p.get(
                                                        "online"
                                                ).getAsBoolean();

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

                    this.minecraft.execute(
                            () -> {

                                friends.clear();

                                friends.addAll(
                                        result
                                );
                            }
                    );
                }
        );
    }

    private void loadRequests() {

        WorldGateModClient.EXECUTOR.submit(
                () -> {

                    String json =
                            WorldGateModClient
                                    .FRIEND_MANAGER
                                    .getIncomingRequests();

                    List<RequestEntry> result =
                            new ArrayList<>();

                    if (json != null
                            && !json.equals("null")) {

                        try {

                            JsonObject object =
                                    JsonParser
                                            .parseString(json)
                                            .getAsJsonObject();

                            for (String uid :
                                    object.keySet()) {

                                JsonObject request =
                                        object.get(uid)
                                                .getAsJsonObject();

                                String name =
                                        request.has(
                                                "fromName"
                                        )
                                                ? request.get(
                                                        "fromName"
                                                ).getAsString()
                                                : "Player";

                                String code =
                                        request.has(
                                                "fromFriendCode"
                                        )
                                                ? request.get(
                                                        "fromFriendCode"
                                                ).getAsString()
                                                : "--------";

                                result.add(
                                        new RequestEntry(
                                                uid,
                                                name,
                                                code
                                        )
                                );
                            }

                        } catch (Exception ignored) {
                        }
                    }

                    this.minecraft.execute(
                            () -> {

                                if (requestSnapshotReady) {

                                    for (
                                            RequestEntry request :
                                            result
                                    ) {

                                        if (
                                                !knownRequestUids
                                                        .contains(
                                                                request.uid()
                                                        )
                                        ) {

                                            showRequestNotification(
                                                    request
                                            );
                                        }
                                    }
                                }

                                knownRequestUids.clear();

                                for (
                                        RequestEntry request :
                                        result
                                ) {

                                    knownRequestUids.add(
                                            request.uid()
                                    );
                                }

                                requestSnapshotReady =
                                        true;
                                                                requests.clear();

                                requests.addAll(
                                        result
                                );

                                if (
                                        selectedRequestUid
                                                != null
                                ) {

                                    boolean stillExists =
                                            requests.stream()
                                                    .anyMatch(
                                                            request ->
                                                                    request.uid()
                                                                            .equals(
                                                                                    selectedRequestUid
                                                                            )
                                                    );

                                    if (!stillExists) {

                                        selectedRequestUid =
                                                null;
                                    }
                                }
                            }
                    );
                }
        );
    }

    private void showRequestNotification(
            RequestEntry request
    ) {

        requestNotification =
                "Friend Request: "
                        + request.name()
                        + " ("
                        + request.code()
                        + ")";

        requestNotificationUntil =
                System.currentTimeMillis()
                        + 5000L;
    }

    private void acceptSelectedRequest() {

        if (selectedRequestUid == null) {

            status =
                    "Select a friend request first.";

            return;
        }

        String uid =
                selectedRequestUid;

        status =
                "Accepting request...";

        WorldGateModClient.EXECUTOR.submit(
                () -> {

                    boolean ok =
                            WorldGateModClient
                                    .FRIEND_MANAGER
                                    .acceptRequest(uid);

                    this.minecraft.execute(
                            () -> {

                                status =
                                        ok
                                                ? "Friend added."
                                                : "Could not accept request.";

                                if (ok) {

                                    selectedRequestUid =
                                            null;

                                    loadAll();
                                }
                            }
                    );
                }
        );
    }

    private void rejectSelectedRequest() {

        if (selectedRequestUid == null) {

            status =
                    "Select a friend request first.";

            return;
        }

        String uid =
                selectedRequestUid;

        status =
                "Rejecting request...";

        WorldGateModClient.EXECUTOR.submit(
                () -> {

                    boolean ok =
                            WorldGateModClient
                                    .FRIEND_MANAGER
                                    .rejectRequest(uid);

                    this.minecraft.execute(
                            () -> {

                                status =
                                        ok
                                                ? "Request rejected."
                                                : "Could not reject request.";

                                if (ok) {

                                    selectedRequestUid =
                                            null;

                                    loadRequests();
                                }
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

        super.extractRenderState(
                graphics,
                mouseX,
                mouseY,
                delta
        );

        int centerX =
                this.width / 2;

        graphics.centeredText(
                this.font,
                "WorldGate Friends",
                centerX,
                20,
                0xFFFFFF
        );

        graphics.centeredText(
                this.font,
                "Your Code: " + myCode,
                centerX,
                40,
                0x55FFFF
        );

        graphics.text(
                this.font,
                "Friend Requests",
                centerX - 140,
                145,
                0xFFFF55
        );

        int requestY =
                160;

        if (requests.isEmpty()) {

            graphics.text(
                    this.font,
                    "No pending requests.",
                    centerX - 140,
                    requestY,
                    0x888888
            );

        } else {

            for (
                    RequestEntry request :
                    requests
            ) {

                boolean selected =
                        request.uid()
                                .equals(
                                        selectedRequestUid
                                );

                int textColor =
                        selected
                                ? 0x55FFFF
                                : 0xFFFFFF;

                graphics.text(
                        this.font,
                        (selected
                                ? "> "
                                : "")
                                + request.name(),
                        centerX - 140,
                        requestY,
                        textColor
                );

                graphics.text(
                        this.font,
                        request.code(),
                        centerX - 140,
                        requestY + 12,
                        0xAAAAAA
                );

                requestY += 32;
            }
        }

        int friendY =
                Math.max(
                        requestY + 15,
                        225
                );

        graphics.text(
                this.font,
                "Friends",
                centerX - 140,
                friendY,
                0x55FF55
        );

        friendY += 18;

        if (friends.isEmpty()) {

            graphics.text(
                    this.font,
                    "No friends yet.",
                    centerX - 140,
                    friendY,
                    0x888888
            );

        } else {

            for (
                    FriendEntry friend :
                    friends
            ) {

                graphics.text(
                        this.font,
                        friend.name(),
                        centerX - 140,
                        friendY,
                        0xFFFFFF
                );

                graphics.text(
                        this.font,
                        friend.code(),
                        centerX - 140,
                        friendY + 12,
                        0xAAAAAA
                );

                String state =
                        friend.online()
                                ? "● Online"
                                : "○ Offline";

                graphics.text(
        this.font,
        state,
        centerX + 140 - this.font.width(state),
        friendY + 5,
        friend.online()
                ? 0x55FF55
                : 0x888888
);

                friendY += 32;
            }
        }

        if (
                requestNotificationUntil
                        > System.currentTimeMillis()
        ) {

            int boxWidth =
                    320;

            int boxHeight =
                    34;

            int boxX =
                    centerX
                            - boxWidth / 2;

            int boxY =
                    5;

            graphics.fill(
                    boxX,
                    boxY,
                    boxX + boxWidth,
                    boxY + boxHeight,
                    0xDD111111
            );

            graphics.centeredText(
                    this.font,
                    requestNotification,
                    centerX,
                    boxY + 12,
                    0x55FFFF
            );
        }

        if (!status.isEmpty()) {

            graphics.centeredText(
                    this.font,
                    status,
                    centerX,
                    this.height - 50,
                    0xAAAAAA
            );
        }
    }

    @Override
public boolean mouseClicked(
        MouseButtonEvent event,
        boolean doubleClick
) {

    double mouseX =
            event.x();

    double mouseY =
            event.y();

    int centerX =
            this.width / 2;

        int requestY =
                160;

        for (
                RequestEntry request :
                requests
        ) {

            if (
                    mouseX >= centerX - 145
                            && mouseX <= centerX + 145
                            && mouseY >= requestY - 4
                            && mouseY <= requestY + 27
            ) {

                selectedRequestUid =
                        request.uid();

                status =
                        "Selected: "
                                + request.name();

                return true;
            }

            requestY += 32;
        }

        return super.mouseClicked(
        event,
        doubleClick
);
    }

    private void closeScreen() {

        WorldGateModClient.FRIEND_MANAGER
                .setFriendListChangedListener(
                        null
                );

        WorldGateModClient.FRIEND_MANAGER
                .stopRealtime();

        WorldGateModClient.FRIEND_MANAGER
                .setOffline();

        this.minecraft.setScreen(
                parent
        );
    }

    @Override
    public void onClose() {

        closeScreen();
    }

    private record FriendEntry(
            String uid,
            String name,
            String code,
            boolean online
    ) {
    }

    private record RequestEntry(
            String uid,
            String name,
            String code
    ) {
    }
}
