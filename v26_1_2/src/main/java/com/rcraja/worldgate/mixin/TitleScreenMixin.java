package com.rcraja.worldgate.mixin;

import com.rcraja.worldgate.client.gui.WorldGateButton;
import com.rcraja.worldgate.client.gui.FriendsScreen;
import com.rcraja.worldgate.client.gui.SettingsScreen;
import com.rcraja.worldgate.client.gui.WardrobeScreen;
import com.rcraja.worldgate.client.gui.EliteProfileScreen;
import com.rcraja.worldgate.client.gui.ClaimCenterScreen;
import com.rcraja.worldgate.client.gui.PicturesScreen;
import com.rcraja.worldgate.client.gui.NotificationsScreen;
import com.rcraja.worldgate.client.gui.IconButton;
import com.rcraja.worldgate.client.gui.WorldGateScreen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Component title) {
        super(title);
    }

    /**
     * Essential-style independent WorldGate overlay.
     * Vanilla Minecraft buttons are only measured, never replaced or modified.
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void worldgate$modernize(CallbackInfo ci) {
        if (minecraft == null || !com.rcraja.worldgate.client.gui.WorldGatePreferences.overlayEnabled()) return;

        int[] b = worldgate$vanillaBounds();
        int left = b[0];
        int top = b[1];
        int right = b[2];

        // Essential-style player preview on the left of the vanilla menu.
        int previewSize = Math.min(126, Math.max(104, height / 2));
        int previewX = Math.max(20, left - previewSize - 250);
        int previewY = Math.max(70, top - 8);
        if (previewX + previewSize <= left - 24) {
            PlayerSkinWidget playerWidget = new PlayerSkinWidget(
                    previewSize, previewSize + 34, minecraft.getEntityModels(),
                    () -> minecraft.playerSkinRenderCache()
                            .getOrDefault(ResolvableProfile.createUnresolved(minecraft.getUser().getProfileId()))
                            .playerSkin());
            playerWidget.setPosition(previewX, previewY);
            addRenderableWidget(playerWidget);

            // Small wardrobe shortcut beneath the player preview.
            addRenderableWidget(new WorldGateButton(
                    previewX + previewSize / 2 - 12, previewY + previewSize + 6, 24, 24,
                    Component.empty(), () -> minecraft.setScreen(new WardrobeScreen(this, WardrobeScreen.Tab.COSMETICS)),
                    0xFFB8C4D0, WorldGateButton.Icon.WARDROBE));
        }

        // Essential-style rail: fixed narrow controls beside the vanilla menu.
        int railW = Math.min(280, Math.max(190, width / 5));
        int railX = width - railW - 26;
        if (railX < right + 12) railX = Math.max(right + 12, width - railW - 12);
        int railY = Math.max(76, top + 4);
        worldgate$addRail(railX, railY, railW);

        // Notification + mailbox remain independent top-right controls.
        addRenderableWidget(new IconButton(width - 76, 18, 34,
                IconButton.Icon.BELL,
                () -> minecraft.setScreen(new NotificationsScreen(this))));
        addRenderableWidget(new IconButton(width - 38, 18, 34,
                IconButton.Icon.MAILBOX,
                () -> minecraft.setScreen(new ClaimCenterScreen(this))));

        // Keep the original WorldGate entry point as a separate overlay button.
        int worldGateY = Math.min(height - 38, b[3] + 14);
        if (worldGateY >= 8 && worldGateY + 28 <= height - 4) {
            addRenderableWidget(new WorldGateButton(
                    Math.max(18, width / 2 - 100), worldGateY, 200, 28,
                    Component.literal("WorldGate"),
                    () -> minecraft.setScreen(new WorldGateScreen(this)),
                    0xFF67D8FF));
        }
    }

    private void worldgate$addRail(int x, int y, int width) {
        int h = 24;
        int gap = 5;
        int w = width;

        addRenderableWidget(new WorldGateButton(x, y, w, h, Component.literal("Host"),
                () -> minecraft.setScreen(new WorldGateScreen(this)), 0xFF67D8FF, WorldGateButton.Icon.HOST));
        addRenderableWidget(new WorldGateButton(x, y + (h + gap), w, h, Component.literal("Social"),
                () -> minecraft.setScreen(new FriendsScreen(this)), 0xFF73E0A1, WorldGateButton.Icon.SOCIAL));
        addRenderableWidget(new WorldGateButton(x, y + 2 * (h + gap), w, h, Component.literal("Wardrobe"),
                () -> minecraft.setScreen(new WardrobeScreen(this, WardrobeScreen.Tab.COSMETICS)), 0xFFFFB86B, WorldGateButton.Icon.WARDROBE));
        addRenderableWidget(new WorldGateButton(x, y + 3 * (h + gap), w, h, Component.literal("Features"),
                () -> minecraft.setScreen(new ClaimCenterScreen(this)), 0xFFBDA6FF, WorldGateButton.Icon.FEATURES));
        addRenderableWidget(new WorldGateButton(x, y + 4 * (h + gap), w, h, Component.literal("Pictures"),
                () -> minecraft.setScreen(new PicturesScreen(this)), 0xFFBDA6FF, WorldGateButton.Icon.PICTURES));
        addRenderableWidget(new WorldGateButton(x, y + 5 * (h + gap), w, h, Component.literal("Settings"),
                () -> minecraft.setScreen(new SettingsScreen(this)), 0xFF9CA9B8, WorldGateButton.Icon.SETTINGS));
        addRenderableWidget(new WorldGateButton(x, y + 6 * (h + gap), w, h, Component.literal("Account"),
                () -> minecraft.setScreen(new EliteProfileScreen(this)), 0xFFFFD36B, WorldGateButton.Icon.ACCOUNT));
    }

    private int[] worldgate$vanillaBounds() {
        int left = width / 2 - 100;
        int top = Math.max(60, height / 4);
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