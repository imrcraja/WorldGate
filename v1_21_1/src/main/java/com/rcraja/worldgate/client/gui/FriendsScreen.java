package com.rcraja.worldgate.client.gui;

import com.rcraja.worldgate.client.WorldGateModClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Simple friends UI (see the 26.1.2 module's FriendsScreen for the design
 * notes -- same behaviour, Yarn mapping names). Minecraft 1.21.1 build.
 */
public class FriendsScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget targetUidBox;

    public FriendsScreen(Screen parent) {
        super(Text.translatable("worldgate.friends.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 80;

        this.client.player.sendMessage(
                Text.translatable("worldgate.friends.your_uid", WorldGateModClient.FRIEND_MANAGER.myUid()), false);

        this.targetUidBox = new TextFieldWidget(this.textRenderer, centerX - 100, y, 200, 20,
                Text.translatable("worldgate.friends.uid_hint"));
        this.targetUidBox.setMaxLength(64);
        this.targetUidBox.setPlaceholder(Text.translatable("worldgate.friends.uid_hint"));
        this.addDrawableChild(this.targetUidBox);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("worldgate.friends.send"), btn ->
                run(() -> WorldGateModClient.FRIEND_MANAGER.sendRequest(targetUidBox.getText()),
                        "worldgate.friends.sent")
        ).dimensions(centerX - 100, y + 25, 97, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("worldgate.friends.accept"), btn ->
                run(() -> WorldGateModClient.FRIEND_MANAGER.acceptRequest(targetUidBox.getText()),
                        "worldgate.friends.accepted")
        ).dimensions(centerX + 3, y + 25, 97, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("worldgate.friends.refresh"), btn ->
                refresh()
        ).dimensions(centerX - 100, y + 50, 200, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("worldgate.button.back"), btn ->
                this.client.setScreen(parent)
        ).dimensions(centerX - 100, y + 85, 200, 20).build());
    }

    private void run(java.util.function.BooleanSupplier action, String successKey) {
        WorldGateModClient.EXECUTOR.submit(() -> {
            boolean ok = action.getAsBoolean();
            this.client.execute(() -> this.client.player.sendMessage(
                    Text.translatable(ok ? successKey : "worldgate.friends.failed"), false));
        });
    }

    private void refresh() {
        WorldGateModClient.EXECUTOR.submit(() -> {
            String requests = WorldGateModClient.FRIEND_MANAGER.getIncomingRequests();
            String friends = WorldGateModClient.FRIEND_MANAGER.getFriends();
            this.client.execute(() -> {
                this.client.player.sendMessage(
                        Text.translatable("worldgate.friends.requests", String.valueOf(requests)), false);
                this.client.player.sendMessage(
                        Text.translatable("worldgate.friends.list", String.valueOf(friends)), false);
            });
        });
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
