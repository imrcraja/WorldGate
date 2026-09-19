package com.rcraja.worldgate.client.gui;

import com.rcraja.worldgate.client.WorldGateModClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Simple friends UI: share your UID, paste a friend's UID to send a
 * request, paste an incoming UID to accept it. Results are printed to
 * chat rather than a scrollable list for now -- a proper list widget is
 * a polish step once this plumbing is confirmed working.
 * Minecraft 26.1.2 (Mojang mappings) build.
 */
public class FriendsScreen extends Screen {
    private final Screen parent;
    private EditBox targetUidBox;

    public FriendsScreen(Screen parent) {
        super(Component.translatable("worldgate.friends.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 80;

        this.minecraft.player.sendSystemMessage(
                Component.translatable("worldgate.friends.your_uid", WorldGateModClient.FRIEND_MANAGER.myUid()));

        this.targetUidBox = new EditBox(this.font, centerX - 100, y, 200, 20,
                Component.translatable("worldgate.friends.uid_hint"));
        this.targetUidBox.setMaxLength(64);
        this.targetUidBox.setHint(Component.translatable("worldgate.friends.uid_hint"));
        this.addRenderableWidget(this.targetUidBox);

        this.addRenderableWidget(Button.builder(Component.translatable("worldgate.friends.send"), btn ->
                run(() -> WorldGateModClient.FRIEND_MANAGER.sendRequest(targetUidBox.getValue()),
                        "worldgate.friends.sent")
        ).bounds(centerX - 100, y + 25, 97, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("worldgate.friends.accept"), btn ->
                run(() -> WorldGateModClient.FRIEND_MANAGER.acceptRequest(targetUidBox.getValue()),
                        "worldgate.friends.accepted")
        ).bounds(centerX + 3, y + 25, 97, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("worldgate.friends.refresh"), btn ->
                refresh()
        ).bounds(centerX - 100, y + 50, 200, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("worldgate.button.back"), btn ->
                this.minecraft.setScreen(parent)
        ).bounds(centerX - 100, y + 85, 200, 20).build());
    }

    private void run(java.util.function.BooleanSupplier action, String successKey) {
        WorldGateModClient.EXECUTOR.submit(() -> {
            boolean ok = action.getAsBoolean();
            this.minecraft.execute(() -> this.minecraft.player.sendSystemMessage(
                    Component.translatable(ok ? successKey : "worldgate.friends.failed")));
        });
    }

    private void refresh() {
        WorldGateModClient.EXECUTOR.submit(() -> {
            String requests = WorldGateModClient.FRIEND_MANAGER.getIncomingRequests();
            String friends = WorldGateModClient.FRIEND_MANAGER.getFriends();
            this.minecraft.execute(() -> {
                this.minecraft.player.sendSystemMessage(
                        Component.translatable("worldgate.friends.requests", String.valueOf(requests)));
                this.minecraft.player.sendSystemMessage(
                        Component.translatable("worldgate.friends.list", String.valueOf(friends)));
            });
        });
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
