package com.rcraja.worldgate.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rcraja.worldgate.client.WorldGateModClient;
import com.rcraja.worldgate.client.elite.EliteBadgeRenderer;
import com.rcraja.worldgate.client.elite.EliteManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FriendsScreen extends Screen {
    private final Screen parent;

    private EditBox targetBox;

    private final List<FriendEntry> friends = new ArrayList<>();
    private final List<RequestEntry> requests = new ArrayList<>();

    private String selectedRequestUid;
    private String selectedFriendUid;

    private String myUid = "Loading...";
    private String myCode = "Loading...";
    private String myName = "Player";
    private String status = "Loading profile...";
    private Button acceptButton;
    private Button rejectButton;
    private Button inviteButton;
    private Button refreshButton;
    private Button joinInviteButton;
    private Button dismissInviteButton;
    private String pendingInviteFromUid;
    private String pendingInviteRoom;
    private final List<InviteEntry> invites = new ArrayList<>();
    private String selectedInviteFromUid;

    private final Set<String> knownRequestUids = new HashSet<>();
    private boolean requestSnapshotReady;
    private String requestNotification = "";
    private long requestNotificationUntil;

    private int profileLeft;
    private int contentTop;
    private int panelWidth;

    public FriendsScreen(Screen parent) {
        super(Component.translatable("worldgate.friends.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int margin = Math.max(12, width / 20);
        panelWidth = Math.max(170, (width - margin * 4) / 3);
        profileLeft = margin;
        contentTop = 58;

        int left = profileLeft + 10;
        int inputWidth = panelWidth - 20;

        targetBox = new EditBox(
                font,
                left,
                contentTop + 46,
                inputWidth,
                20,
                Component.translatable("worldgate.friends.uid_hint")
        );
        targetBox.setMaxLength(128);
        targetBox.setHint(Component.literal("UID or Friend Code"));
        addRenderableWidget(targetBox);

        addRenderableWidget(Button.builder(
                Component.translatable("worldgate.friends.add"),
                btn -> sendRequest()
        ).bounds(left, contentTop + 71, inputWidth, 20).build());

        int center = profileLeft + panelWidth + margin;
        int requestsWidth = panelWidth;

        acceptButton = Button.builder(Component.translatable("worldgate.friends.accept"),
                btn -> acceptSelectedRequest())
                .bounds(center, height - 52, (requestsWidth - 5) / 2, 20).build();
        rejectButton = Button.builder(Component.translatable("worldgate.friends.reject"),
                btn -> rejectSelectedRequest())
                .bounds(center + (requestsWidth + 5) / 2, height - 52, (requestsWidth - 5) / 2, 20).build();
        addRenderableWidget(acceptButton);
        addRenderableWidget(rejectButton);

        int right = center + panelWidth + margin;
        inviteButton = Button.builder(Component.literal("Invite Selected"),
                btn -> inviteSelectedFriend())
                .bounds(right + 10, height - 52, panelWidth - 20, 20).build();
        addRenderableWidget(inviteButton);

        joinInviteButton = Button.builder(Component.literal("Join Invite"), b -> joinPendingInvite())
                .bounds(center, height - 78, (requestsWidth - 5) / 2, 20).build();
        dismissInviteButton = Button.builder(Component.literal("Dismiss"), b -> dismissPendingInvite())
                .bounds(center + (requestsWidth + 5) / 2, height - 78, (requestsWidth - 5) / 2, 20).build();
        addRenderableWidget(joinInviteButton);
        addRenderableWidget(dismissInviteButton);

        refreshButton = Button.builder(Component.literal("Refresh"),
                btn -> loadAll())
                .bounds(left, height - 52, inputWidth, 20).build();
        addRenderableWidget(refreshButton);
        updateButtons();

        addRenderableWidget(Button.builder(
                Component.translatable("worldgate.button.back"),
                btn -> closeScreen()
        ).bounds(width - margin - 110, 12, 110, 20).build());

        loadProfile();

        WorldGateModClient.FRIEND_MANAGER.setFriendListChangedListener(ignored -> {
            if (minecraft != null) {
                minecraft.execute(this::loadAll);
            }
        });
        WorldGateModClient.FRIEND_MANAGER.startRealtime();

        WorldGateModClient.ROOM_MANAGER.setInviteChangedListener(ignored -> {
            if (minecraft != null) {
                minecraft.execute(this::loadInvites);
            }
        });
        WorldGateModClient.ROOM_MANAGER.startInviteRealtime();

        loadAll();
    }

    private void loadProfile() {
        WorldGateModClient.EXECUTOR.submit(() -> {
            String uid = WorldGateModClient.FRIEND_MANAGER.myUid();
            String code = WorldGateModClient.FRIEND_MANAGER.myFriendCode();
            String name = minecraft != null ? minecraft.getUser().getName() : "Player";

            WorldGateModClient.FRIEND_MANAGER.setOnline(name);
            String profile = WorldGateModClient.FRIEND_MANAGER.getProfile(uid);

            String resolvedName = name;
            if (profile != null && !profile.equals("null")) {
                try {
                    JsonObject object = JsonParser.parseString(profile).getAsJsonObject();
                    if (object.has("displayName")) {
                        resolvedName = object.get("displayName").getAsString();
                    }
                    if (object.has("friendCode")) {
                        code = object.get("friendCode").getAsString();
                    }
                } catch (Exception ignored) {
                }
            }

            final String finalUid = uid == null ? "Unavailable" : uid;
            final String finalCode = code == null ? "Unavailable" : code;
            final String finalName = resolvedName;

            if (minecraft != null) {
                minecraft.execute(() -> {
                    myUid = finalUid;
                    myCode = finalCode;
                    myName = finalName;
                    status = "Profile ready";
                });
            }
        });
    }

    private void updateButtons() {
        if (acceptButton == null) return;
        acceptButton.active = selectedRequestUid != null;
        rejectButton.active = selectedRequestUid != null;
        inviteButton.active = selectedFriendUid != null && WorldGateModClient.CURRENT_ROOM_CODE != null && !WorldGateModClient.CURRENT_ROOM_CODE.isBlank();
        refreshButton.active = true;
        joinInviteButton.active = pendingInviteRoom != null && !pendingInviteRoom.isBlank();
        dismissInviteButton.active = pendingInviteFromUid != null && !pendingInviteFromUid.isBlank();
    }

    private void sendRequest() {
        String target = targetBox.getValue().trim();
        if (target.isEmpty()) {
            status = "Enter a UID or Friend Code.";
            return;
        }

        status = "Sending friend request...";
        WorldGateModClient.EXECUTOR.submit(() -> {
            boolean ok = WorldGateModClient.FRIEND_MANAGER.sendRequest(target);
            if (minecraft != null) {
                minecraft.execute(() -> status =
                        ok ? "Friend request sent." : "UID / Friend Code not found.");
            }
        });
    }

    private void loadAll() {
        loadFriends();
        loadRequests();
        loadInvites();
    }

    private void loadInvites() {
        WorldGateModClient.EXECUTOR.submit(() -> {
            String json = WorldGateModClient.ROOM_MANAGER.getIncomingInvites();

            try {
                JsonObject inviteObject = (json == null || json.equals("null") || json.isBlank())
                        ? new JsonObject()
                        : JsonParser.parseString(json).getAsJsonObject();

                this.invites.clear();
                if (inviteObject.entrySet().isEmpty()) {
                    if (minecraft != null) minecraft.execute(() -> {
                        pendingInviteFromUid = null;
                        pendingInviteRoom = null;
                        selectedInviteFromUid = null;
                        requestNotification = "";
                        updateButtons();
                    });
                    return;
                }

                for (String inviteUid : inviteObject.keySet()) {
                    JsonObject inviteData = inviteObject.getAsJsonObject(inviteUid);
                    String inviteName = inviteData.has("fromName") ? inviteData.get("fromName").getAsString() : "Player";
                    String inviteRoom = inviteData.has("roomCode") ? inviteData.get("roomCode").getAsString() : "";
                    this.invites.add(new InviteEntry(inviteUid, inviteName, inviteRoom));
                }
                String fromUid = selectedInviteFromUid;
                if (fromUid == null || this.invites.stream().noneMatch(i -> i.fromUid().equals(fromUid))) {
                    fromUid = this.invites.get(0).fromUid();
                }
                JsonObject invite = inviteObject.getAsJsonObject(fromUid);
                for (String inviteUid : invites.keySet()) {
                    JsonObject inviteObject = invites.getAsJsonObject(inviteUid);
                    String inviteName = inviteObject.has("fromName") ? inviteObject.get("fromName").getAsString() : "Player";
                    String inviteRoom = inviteObject.has("roomCode") ? inviteObject.get("roomCode").getAsString() : "";
                    this.invites.add(new InviteEntry(inviteUid, inviteName, inviteRoom));
                }
                String fromUid = invites.keySet().iterator().next();
                JsonObject invite = invites.getAsJsonObject(fromUid);
                String name = invite.has("fromName")
                        ? invite.get("fromName").getAsString()
                        : "Player";
                String room = invite.has("roomCode")
                        ? invite.get("roomCode").getAsString()
                        : "";

                if (minecraft != null) {
                    minecraft.execute(() -> {
                        selectedInviteFromUid = fromUid;
                        pendingInviteFromUid = fromUid;
                        pendingInviteRoom = room;
                        requestNotification = "Room Invite: " + name + " [" + room + "]";
                        requestNotificationUntil = System.currentTimeMillis() + 7000L;
                        status = "Room invite received: " + room;
                    });
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void joinPendingInvite() {
        if (pendingInviteRoom == null || pendingInviteRoom.isBlank()) {
            status = "No room invite selected.";
            return;
        }
        String room = pendingInviteRoom;
        status = "Joining invited room...";
        WorldGateScreen target = parent instanceof WorldGateScreen screen
                ? screen
                : new WorldGateScreen(parent);
        if (minecraft != null) {
            minecraft.setScreen(target);
            target.joinRoomFromInvite(room);
        }
    }

    private void dismissPendingInvite() {
        String from = pendingInviteFromUid;
        if (from == null || from.isBlank()) return;
        WorldGateModClient.EXECUTOR.submit(() -> {
            boolean ok = WorldGateModClient.ROOM_MANAGER.removeInvite(from);
            if (minecraft != null) minecraft.execute(() -> {
                pendingInviteFromUid = null;
                pendingInviteRoom = null;
                selectedInviteFromUid = null;
                requestNotification = "";
                status = ok ? "Room invite dismissed." : "Could not dismiss room invite.";
                updateButtons();
            });
        });
    }

    private void inviteSelectedFriend() {
        if (selectedFriendUid == null) {
            status = "Select a friend first.";
            return;
        }

        String room = WorldGateModClient.CURRENT_ROOM_CODE;
        if (room == null || room.isBlank()) {
            status = "Create or join a room first.";
            return;
        }

        String uid = selectedFriendUid;
        status = "Sending room invite...";

        WorldGateModClient.EXECUTOR.submit(() -> {
            boolean ok = WorldGateModClient.ROOM_MANAGER.inviteFriend(
                    room,
                    uid,
                    minecraft.getUser().getName()
            );
            if (minecraft != null) {
                minecraft.execute(() ->
                        status = ok ? "Room invite sent." : "Could not send room invite.");
            }
        });
    }

    private void loadFriends() {
        WorldGateModClient.EXECUTOR.submit(() -> {
            String json = WorldGateModClient.FRIEND_MANAGER.getFriends();
            List<FriendEntry> result = new ArrayList<>();

            if (json != null && !json.equals("null")) {
                try {
                    JsonObject object = JsonParser.parseString(json).getAsJsonObject();
                    for (String uid : object.keySet()) {
                        String profile = WorldGateModClient.FRIEND_MANAGER.getProfile(uid);
                        if (profile == null || profile.equals("null")) {
                            continue;
                        }

                        JsonObject p = JsonParser.parseString(profile).getAsJsonObject();
                        String name = p.has("displayName")
                                ? p.get("displayName").getAsString()
                                : "Player";
                        String code = p.has("friendCode")
                                ? p.get("friendCode").getAsString()
                                : "--------";
                        boolean online = p.has("online") && p.get("online").getAsBoolean();
                        int eliteLevel = EliteManager.loadProfile(uid).level();

                        result.add(new FriendEntry(uid, name, code, online, eliteLevel));
                    }
                } catch (Exception ignored) {
                }
            }

            if (minecraft != null) {
                minecraft.execute(() -> {
                    friends.clear();
                    friends.addAll(result);
                    if (selectedFriendUid != null
                            && friends.stream().noneMatch(f -> f.uid().equals(selectedFriendUid))) {
                        selectedFriendUid = null;
                    }
                    updateButtons();
                });
            }
        });
    }

    private void loadRequests() {
        WorldGateModClient.EXECUTOR.submit(() -> {
            String json = WorldGateModClient.FRIEND_MANAGER.getIncomingRequests();
            List<RequestEntry> result = new ArrayList<>();

            if (json != null && !json.equals("null")) {
                try {
                    JsonObject object = JsonParser.parseString(json).getAsJsonObject();
                    for (String uid : object.keySet()) {
                        JsonObject request = object.get(uid).getAsJsonObject();
                        String name = request.has("fromName")
                                ? request.get("fromName").getAsString()
                                : "Player";
                        String code = request.has("fromFriendCode")
                                ? request.get("fromFriendCode").getAsString()
                                : "--------";
                        result.add(new RequestEntry(uid, name, code));
                    }
                } catch (Exception ignored) {
                }
            }

            if (minecraft != null) {
                minecraft.execute(() -> {
                    if (requestSnapshotReady) {
                        for (RequestEntry request : result) {
                            if (!knownRequestUids.contains(request.uid())) {
                                showRequestNotification(request);
                            }
                        }
                    }

                    knownRequestUids.clear();
                    for (RequestEntry request : result) {
                        knownRequestUids.add(request.uid());
                    }

                    requestSnapshotReady = true;
                    requests.clear();
                    requests.addAll(result);

                    if (selectedRequestUid != null
                            && requests.stream().noneMatch(r -> r.uid().equals(selectedRequestUid))) {
                        selectedRequestUid = null;
                    }
                    updateButtons();
                });
            }
        });
    }

    private void showRequestNotification(RequestEntry request) {
        requestNotification =
                "Friend Request: " + request.name() + " (" + request.code() + ")";
        requestNotificationUntil = System.currentTimeMillis() + 5000L;
    }

    private void acceptSelectedRequest() {
        if (selectedRequestUid == null) {
            status = "Select a friend request first.";
            return;
        }

        String uid = selectedRequestUid;
        status = "Accepting request...";

        WorldGateModClient.EXECUTOR.submit(() -> {
            boolean ok = WorldGateModClient.FRIEND_MANAGER.acceptRequest(uid);
            if (minecraft != null) {
                minecraft.execute(() -> {
                    status = ok ? "Friend added." : "Could not accept request.";
                    if (ok) {
                        selectedRequestUid = null;
                        updateButtons();
                        loadAll();
                    }
                });
            }
        });
    }

    private void rejectSelectedRequest() {
        if (selectedRequestUid == null) {
            status = "Select a friend request first.";
            return;
        }

        String uid = selectedRequestUid;
        status = "Rejecting request...";

        WorldGateModClient.EXECUTOR.submit(() -> {
            boolean ok = WorldGateModClient.FRIEND_MANAGER.rejectRequest(uid);
            if (minecraft != null) {
                minecraft.execute(() -> {
                    status = ok ? "Request rejected." : "Could not reject request.";
                    if (ok) {
                        selectedRequestUid = null;
                        updateButtons();
                        loadRequests();
                    }
                });
            }
        });
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float delta
    ) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int margin = Math.max(12, width / 20);
        int gap = margin;
        int left = margin;
        int center = left + panelWidth + gap;
        int right = center + panelWidth + gap;

        graphics.centeredText(
                font,
                "WorldGate Social",
                width / 2,
                18,
                0xFFFFFFFF
        );
        graphics.centeredText(
                font,
                "Friends, requests and player identity",
                width / 2,
                34,
                0xFF8F9BA8
        );

        drawPanel(graphics, left, contentTop, panelWidth, height - contentTop - 78);
        drawPanel(graphics, center, contentTop, panelWidth, height - contentTop - 78);
        drawPanel(graphics, right, contentTop, panelWidth, height - contentTop - 78);

        graphics.text(font, "YOUR PROFILE", left + 10, contentTop + 10, 0xFF7DE2FF);
        graphics.text(font, myName, left + 10, contentTop + 25, 0xFFFFFFFF);
        graphics.text(font, "UID", left + 10, contentTop + 101, 0xFF8F9BA8);
        drawClippedText(graphics, myUid, left + 10, contentTop + 113, panelWidth - 20, 0xFFD8DDE3);
        graphics.text(font, "FRIEND CODE", left + 10, contentTop + 134, 0xFF8F9BA8);
        graphics.text(font, myCode, left + 10, contentTop + 146, 0xFF7DE2FF);
        graphics.text(font, "Send a request by UID or code", left + 10, contentTop + 32, 0xFF7F8A96);

        graphics.text(font, "FRIEND REQUESTS", center + 10, contentTop + 10, 0xFFFFD166);
        int requestY = contentTop + 30;
        if (requests.isEmpty()) {
            graphics.text(font, "No pending requests.", center + 10, requestY, 0xFF7F8A96);
        } else {
            for (RequestEntry request : requests) {
                boolean selected = request.uid().equals(selectedRequestUid);
                if (selected) {
                    graphics.fill(center + 6, requestY - 4, center + panelWidth - 6, requestY + 31, 0x5526C6DA);
                }
                graphics.text(
                        font,
                        request.name(),
                        center + 12,
                        requestY,
                        selected ? 0xFF7DE2FF : 0xFFFFFFFF
                );
                graphics.text(font, request.code(), center + 12, requestY + 13, 0xFF8F9BA8);
                requestY += 38;
                if (requestY > height - 92) break;
            }
        }

        graphics.text(font, "FRIENDS", right + 10, contentTop + 10, 0xFF70E090);
        int friendY = contentTop + 30;
        if (friends.isEmpty()) {
            graphics.text(font, "No friends yet.", right + 10, friendY, 0xFF7F8A96);
        } else {
            for (FriendEntry friend : friends) {
                boolean selected = friend.uid().equals(selectedFriendUid);
                if (selected) {
                    graphics.fill(right + 6, friendY - 4, right + panelWidth - 6, friendY + 31, 0x5533CC77);
                }

                if (friend.eliteLevel() > 0) {
                    EliteBadgeRenderer.draw(graphics, font, right + 12, friendY - 5, 22, friend.eliteLevel());
                }

                int textX = friend.eliteLevel() > 0 ? right + 40 : right + 12;
                graphics.text(font, friend.name(), textX, friendY, 0xFFFFFFFF);
                graphics.text(font, friend.code(), textX, friendY + 13, 0xFF8F9BA8);

                String state = friend.online() ? "Online" : "Offline";
                int stateX = right + panelWidth - 12 - font.width(state);
                graphics.text(font, state, stateX, friendY + 5,
                        friend.online() ? 0xFF70E090 : 0xFF6F7780);

                friendY += 38;
                if (friendY > height - 92) break;
            }
        }

        if (requestNotificationUntil > System.currentTimeMillis()) {
            int boxWidth = Math.min(420, width - 24);
            int boxX = (width - boxWidth) / 2;
            graphics.fill(boxX, 3, boxX + boxWidth, 28, 0xEE111820);
            graphics.outline(boxX, 3, boxWidth, 25, 0xFF2C4052);
            graphics.centeredText(font, requestNotification, width / 2, 10, 0xFF7DE2FF);
        }

        updateButtons();

        if (pendingInviteRoom != null && !pendingInviteRoom.isBlank()) {
            graphics.text(font, "ROOM INVITE", center + 10, contentTop + 10, 0xFFB8A7FF);
            graphics.text(font, "Room " + pendingInviteRoom, center + 10, contentTop + 27, 0xFFFFFFFF);
            graphics.text(font, "Join or dismiss the invitation below.", center + 10, contentTop + 41, 0xFF8F9BA8);
        }

        if (!status.isEmpty()) {
            graphics.centeredText(font, status, width / 2, height - 28, 0xFF9BA7B3);
        }
    }

    private void drawPanel(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int w,
            int h
    ) {
        graphics.fill(x, y, x + w, y + h, 0xCC10161D);
        graphics.outline(x, y, w, h, 0xFF2B3742);
    }

    private void drawClippedText(
            GuiGraphicsExtractor graphics,
            String value,
            int x,
            int y,
            int maxWidth,
            int color
    ) {
        String text = value == null ? "" : value;
        while (font.width(text) > maxWidth && text.length() > 4) {
            text = text.substring(0, text.length() - 1);
        }
        if (!text.equals(value)) {
            text = text.substring(0, Math.max(1, text.length() - 1)) + "...";
        }
        graphics.text(font, text, x, y, color);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        int margin = Math.max(12, width / 20);
        int center = margin + panelWidth + margin;
        int right = center + panelWidth + margin;

        int requestY = contentTop + 30;
        for (RequestEntry request : requests) {
            if (mouseX >= center + 6
                    && mouseX <= center + panelWidth - 6
                    && mouseY >= requestY - 4
                    && mouseY <= requestY + 31) {
                selectedRequestUid = request.uid();
                status = "Selected request: " + request.name();
                return true;
            }
            requestY += 38;
            if (requestY > height - 92) break;
        }

        int friendY = contentTop + 30;
        for (FriendEntry friend : friends) {
            if (mouseX >= right + 6
                    && mouseX <= right + panelWidth - 6
                    && mouseY >= friendY - 4
                    && mouseY <= friendY + 31) {
                selectedFriendUid = friend.uid();
                status = "Selected friend: " + friend.name();
                return true;
            }
            friendY += 38;
            if (friendY > height - 92) break;
        }

        return super.mouseClicked(event, doubleClick);
    }

    private void closeScreen() {
        WorldGateModClient.FRIEND_MANAGER.setFriendListChangedListener(null);
        WorldGateModClient.FRIEND_MANAGER.stopRealtime();
        WorldGateModClient.ROOM_MANAGER.setInviteChangedListener(null);
        WorldGateModClient.ROOM_MANAGER.stopInviteRealtime();
        WorldGateModClient.FRIEND_MANAGER.setOffline();

        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void onClose() {
        closeScreen();
    }

    private record FriendEntry(
            String uid,
            String name,
            String code,
            boolean online,
            int eliteLevel
    ) {}

    private record RequestEntry(
            String uid,
            String name,
            String code
    ) {}

    private record InviteEntry(
            String fromUid,
            String fromName,
            String roomCode
    ) {}
}
