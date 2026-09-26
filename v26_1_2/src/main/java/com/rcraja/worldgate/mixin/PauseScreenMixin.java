package com.rcraja.worldgate.mixin;

import com.rcraja.worldgate.client.gui.WorldGateButton;
import com.rcraja.worldgate.client.gui.WorldGateScreen;
import com.rcraja.worldgate.client.gui.FriendsScreen;
import com.rcraja.worldgate.client.gui.SettingsScreen;
import com.rcraja.worldgate.client.gui.WardrobeScreen;
import com.rcraja.worldgate.client.gui.EliteProfileScreen;
import com.rcraja.worldgate.client.gui.ClaimCenterScreen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    /**
     * WorldGate is an independent overlay layer.
     * Vanilla pause buttons remain untouched and keep their original widget,
     * coordinates, size and click action.
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void worldgate$modernize(CallbackInfo ci) {
        if (this.minecraft == null) return;

        int[] vanillaBounds = worldgate$vanillaBounds();

        if (width >= 760) {
            int railW = Math.min(196, Math.max(176, width / 6));
            int railX = Math.min(width - railW - 18, vanillaBounds[2] + 28);
            int railY = Math.max(70, vanillaBounds[1] - 4);
            if (railX + railW <= width - 8) {
                worldgate$addRail(railX, railY, railW);
            }
        }

        int previewSize = width >= 900 ? 148 : 116;
        int previewX = width >= 760
                ? Math.max(8, Math.min(width - previewSize - 12, vanillaBounds[0] - previewSize - 26))
                : Math.max(8, width - previewSize - 12);
        int previewY = Math.max(44, vanillaBounds[1] - 18);

        if (previewX >= 8 && previewX + previewSize <= width - 8 && previewY + previewSize + 34 <= height - 8) {
            PlayerSkinWidget playerWidget = new PlayerSkinWidget(
                    previewSize,
                    previewSize + 34,
                    this.minecraft.getEntityModels(),
                    () -> this.minecraft.playerSkinRenderCache()
                            .getOrDefault(ResolvableProfile.createUnresolved(this.minecraft.getUser().getProfileId()))
                            .playerSkin()
            );
            playerWidget.setPosition(previewX, previewY);
            this.addRenderableWidget(playerWidget);
        }

        int worldGateY = Math.min(height - 38, vanillaBounds[3] + 14);
        if (worldGateY >= 8 && worldGateY + 28 <= height - 4) {
            addRenderableWidget(new WorldGateButton(
                    Math.max(18, width / 2 - 100), worldGateY, 200, 28,
                    Component.literal("WorldGate"),
                    () -> this.minecraft.setScreen(new WorldGateScreen(this)),
                    0xFF67D8FF));
        }
    }

    private void worldgate$addRail(int x, int y, int width) {
        int buttonW = width - 16;
        int buttonH = 30;
        int gap = 7;

        addRenderableWidget(new WorldGateButton(x + 8, y, buttonW, buttonH,
                Component.literal("Host"),
                () -> this.minecraft.setScreen(new WorldGateScreen(this)), 0xFF67D8FF));
        addRenderableWidget(new WorldGateButton(x + 8, y + (buttonH + gap), buttonW, buttonH,
                Component.literal("Social"),
                () -> this.minecraft.setScreen(new FriendsScreen(this)), 0xFF73E0A1));
        addRenderableWidget(new WorldGateButton(x + 8, y + 2 * (buttonH + gap), buttonW, buttonH,
                Component.literal("Wardrobe"),
                () -> this.minecraft.setScreen(new WardrobeScreen(this, WardrobeScreen.Tab.COSMETICS)), 0xFFFFB86B));
        addRenderableWidget(new WorldGateButton(x + 8, y + 3 * (buttonH + gap), buttonW, buttonH,
                Component.literal("Features"),
                () -> this.minecraft.setScreen(new ClaimCenterScreen(this)), 0xFFBDA6FF));
        addRenderableWidget(new WorldGateButton(x + 8, y + 4 * (buttonH + gap), buttonW, buttonH,
                Component.literal("Settings"),
                () -> this.minecraft.setScreen(new SettingsScreen(this)), 0xFF9CA9B8));
        addRenderableWidget(new WorldGateButton(x + 8, y + 5 * (buttonH + gap), buttonW, buttonH,
                Component.literal("Account"),
                () -> this.minecraft.setScreen(new EliteProfileScreen(this)), 0xFFFFD36B));
    }

    private int[] worldgate$vanillaBounds() {
        int left = width / 2 - 100;
        int top = Math.max(54, height / 2 - 110);
        int right = width / 2 + 100;
        int bottom = top + 30;

        for (var child : this.children()) {
            if (child instanceof Button button && button.visible) {
                left = Math.min(left, button.getX());
                top = Math.min(top, button.getY());
                right = Math.max(right, button.getRight());
                bottom = Math.max(bottom, button.getBottom());
            }
        }
        return new int[] { left, top, right, bottom };
    }
}
