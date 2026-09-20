package com.rcraja.worldgate.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rcraja.worldgate.client.WorldGateModClient;

import net.client.client.gui.DrawContext;
import net.client.client.gui.widget.ButtonWidget;
import net.client.client.gui.widget.TextFieldWidget;
import net.client.client.gui.screen.Screen;
import net.client.text.Text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FriendsScreen extends Screen {

    private final Screen parent;

    private TextFieldWidget friendCodeBox;

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
                Text.translatable(
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
                new TextFieldWidget(
                        this.textRenderer,
                        centerX - 100,
                        60,
                        200,
                        20,
                        Text.literal(
                                "Friend Code"
                        )
                );

        friendCodeBox.setMaxLength(12);

        friendCodeBox.setPlaceholder(
                Text.literal(
                        "Enter Friend Code"
                )
        );

        this.addDrawableChild(
                friendCodeBox
        );

        this.addDrawableChild(
                ButtonWidget.builder(
                        Text.literal(
                                "Add Friend"
                        ),
                        btn -> sendRequest()
                )
                .dimensions(
                        centerX - 100,
                        85,
                        200,
                        20
                )
                .build()
        );

        this.addDrawableChild(
                ButtonWidget.builder(
                        Text.literal(
                                "Accept"
                        ),
                        btn -> acceptSelectedRequest()
                )
                .dimensions(
                        centerX - 100,
                        110,
                        97,
                        20
                )
                .build()
        );

        this.addDrawableChild(
                ButtonWidget.builder(
                        Text.literal(
                                "Reject"
                        ),
                        btn -> rejectSelectedRequest()
                )
                .dimensions(
                        centerX + 3,
                        110,
                        97,
                        20
                )
                .build()
        );

        this.addDrawableChild(
                ButtonWidget.builder(
                        Text.literal(
                                "Back"
                        ),
                        btn -> closeScreen()
                )
                .dimensions(
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

                            if (this.client != null) {

                                this.client.execute(
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
                            this.client
                                    .getUser()
                                    .getName();

                    WorldGateModClient
                            .FRIEND_MANAGER
                            .setOnline(name);

                    this.client.execute(
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
                        .getText()
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

                    this.client.execute(
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

                    this.client.execute(
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

                    this.client.execute(
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

                    this.client.execute(
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

                    this.client.execute(
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
    public void render(
            GuiGraphicsExtractor graphics,
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

        int centerX =
                this.width / 2;

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                "WorldGate Friends",
                centerX,
                20,
                0xFFFFFF
        );

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                "Your Code: " + myCode,
                centerX,
                40,
                0x55FFFF
        );

        context.drawTextWithShadow(
                this.textRenderer,
                "Friend Requests",
                centerX - 140,
                145,
                0xFFFF55
        );

        int requestY =
                160;

        if (requests.isEmpty()) {

            context.drawTextWithShadow(
                    this.textRenderer,
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

                context.drawTextWithShadow(
                        this.textRenderer,
                        (selected
                                ? "> "
                                : "")
                                + request.name(),
                        centerX - 140,
                        requestY,
                        textColor
                );

                context.drawTextWithShadow(
                        this.textRenderer,
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

        context.drawTextWithShadow(
                this.textRenderer,
                "Friends",
                centerX - 140,
                friendY,
                0x55FF55
        );

        friendY += 18;

        if (friends.isEmpty()) {

            context.drawTextWithShadow(
                    this.textRenderer,
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

                context.drawTextWithShadow(
                        this.textRenderer,
                        friend.name(),
                        centerX - 140,
                        friendY,
                        0xFFFFFF
                );

                context.drawTextWithShadow(
                        this.textRenderer,
                        friend.code(),
                        centerX - 140,
                        friendY + 12,
                        0xAAAAAA
                );

                String state =
                        friend.online()
                                ? "● Online"
                                : "○ Offline";

                context.drawTextWithShadow(
        this.textRenderer,
        state,
        centerX + 140 - this.textRenderer.width(state),
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

            context.fill(
                    boxX,
                    boxY,
                    boxX + boxWidth,
                    boxY + boxHeight,
                    0xDD111111
            );

            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    requestNotification,
                    centerX,
                    boxY + 12,
                    0x55FFFF
            );
        }

        if (!status.isEmpty()) {

            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    status,
                    centerX,
                    this.height - 50,
                    0xAAAAAA
            );
        }
    }

    @Override
public boolean mouseClicked(double mouseX, double mouseY, int button) {

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

        return super.mouseClicked(mouseX, mouseY, button);
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

        this.client.setScreen(
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
