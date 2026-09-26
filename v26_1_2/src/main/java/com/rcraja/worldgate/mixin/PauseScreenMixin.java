package com.rcraja.worldgate.mixin;

import com.rcraja.worldgate.client.gui.WorldGateButton;
import com.rcraja.worldgate.client.gui.WorldGateScreen;
import com.rcraja.worldgate.client.gui.FriendsScreen;
import com.rcraja.worldgate.client.gui.SettingsScreen;
import com.rcraja.worldgate.client.gui.WardrobeScreen;
import com.rcraja.worldgate.client.gui.EliteProfileScreen;
import com.rcraja.worldgate.client.gui.ClaimCenterScreen;
import com.rcraja.worldgate.client.gui.PicturesScreen;
import com.rcraja.worldgate.client.gui.NotificationsScreen;
import com.rcraja.worldgate.client.gui.IconButton;

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
     * Essential-style independent WorldGate overlay.
     * Vanilla pause controls are only measured, never replaced or modified.
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void worldgate$modernize(CallbackInfo ci) {
        if (minecraft == null || !com.rcraja.worldgate.client.gui.WorldGatePreferences.overlayEnabled()) return;

        int[] b = worldgate$vanillaBounds();
        int left = b[0];
        int top = b[1];
        int right = b[2];

        int previewSize = Math.min(98, Math.max(82, height / 3));
        int previewX = Math.max(20, left - previewSize - 250);
        int previewY = Math.max(70, top - 8);
        if (previewX + previewSize <= left - 24) {
            PlayerSkinWidget playerWidget = new PlayerSkinWidget(
                    previewSize, previewSize + 28, minecraft.getEntityModels(),
                    () -> minecraft.playerSkinRenderCache()
                            .getOrDefault(ResolvableProfile.createUnresolved(minecraft.getUser().getProfileId()))
                            .playerSkin());
            playerWidget.setPosition(previewX, previewY);
            addRenderableWidget(playerWidget);

            addRenderableWidget(new WorldGateButton(
                    previewX + previewSize / 2 - 10, previewY + previewSize + 5, 20, 20,
                    Component.empty(), () -> minecraft.setScreen(new WardrobeScreen(this, WardrobeScreen.Tab.COSMETICS)),
                    0xFFB8C4D0, WorldGateButton.Icon.WARDROBE));
        }

        int railW = Math.min(216, Math.max(184, width / 8));
        int railX = width - railW - 24;
        if (railX < right + 12) railX = Math.max(right + 12, width - railW - 12);
        int railY = Math.max(68, top + 2);
        worldgate$addRail(railX, railY, railW);

        addRenderableWidget(new IconButton(width - 76, 18, 34,
                IconButton.Icon.BELL,
                () -> minecraft.setScreen(new NotificationsScreen(this))));
        addRenderableWidget(new IconButton(width - 38, 18, 34,
                IconButton.Icon.MAILBOX,
                () -> minecraft.setScreen(new ClaimCenterScreen(this))));

        // Keep the original WorldGate entry point as a separate overlay button.
        int worldGateY = Math.min(height - 34, b[3] + 8);
        if (worldGateY >= 8 && worldGateY + 28 <= height - 4) {
            addRenderableWidget(new WorldGateButton(
                    Math.max(18, width / 2 - 96), worldGateY, 192, 26,
                    Component.literal("WorldGate"),
                    () -> minecraft.setScreen(new WorldGateScreen(this)),
                    0xFF67D8FF));
        }
    }

    private void worldgate$addRail(int x, int y, int width) {
        int h = 28;
        int gap = 6;

        addRenderableWidget(new WorldGateButton(x, y, width, h, Component.literal("Host"),
                () -> minecraft.setScreen(new WorldGateScreen(this)), 0xFF67D8FF, WorldGateButton.Icon.HOST));
        addRenderableWidget(new WorldGateButton(x, y + (h + gap), width, h, Component.literal("Social"),
                () -> minecraft.setScreen(new FriendsScreen(this)), 0xFF73E0A1, WorldGateButton.Icon.SOCIAL));
        addRenderableWidget(new WorldGateButton(x, y + 2 * (h + gap), width, h, Component.literal("Wardrobe"),
                () -> minecraft.setScreen(new WardrobeScreen(this, WardrobeScreen.Tab.COSMETICS)), 0xFFFFB86B, WorldGateButton.Icon.WARDROBE));
        addRenderableWidget(new WorldGateButton(x, y + 3 * (h + gap), width, h, Component.literal("Features"),
                () -> minecraft.setScreen(new ClaimCenterScreen(this)), 0xFFBDA6FF, WorldGateButton.Icon.FEATURES));
        addRenderableWidget(new WorldGateButton(x, y + 4 * (h + gap), width, h, Component.literal("Pictures"),
                () -> minecraft.setScreen(new PicturesScreen(this)), 0xFFBDA6FF, WorldGateButton.Icon.PICTURES));
        addRenderableWidget(new WorldGateButton(x, y + 5 * (h + gap), width, h, Component.literal("Settings"),
                () -> minecraft.setScreen(new SettingsScreen(this)), 0xFF9CA9B8, WorldGateButton.Icon.SETTINGS));
        addRenderableWidget(new WorldGateButton(x, y + 6 * (h + gap), width, h, Component.literal("Account"),
                () -> minecraft.setScreen(new EliteProfileScreen(this)), 0xFFFFD36B, WorldGateButton.Icon.ACCOUNT));
    }

    private int[] worldgate$vanillaBounds() {
        int left = width / 2 - 100;
        int top = Math.max(54, height / 2 - 110);
        int right = width / 2 + 100;
        int bottom = top + 30;

        for (var child : children()) {
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