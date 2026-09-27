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
import com.rcraja.worldgate.client.gui.WorldGateMenuProfile;

import net.minecraft.client.gui.components.Button;
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
        if (minecraft == null) return;

        int[] b = worldgate$vanillaBounds();
        int top = b[1];

        // Keep vanilla controls untouched; WorldGate owns the two side panels.
        int panelW = Math.min(278, Math.max(238, width / 5));
        int panelH = Math.min(470, Math.max(360, height - 180));
        int panelX = 18;
        int panelY = Math.max(74, (height - panelH) / 2 + 20);
        addRenderableWidget(new WorldGateMenuProfile(panelX, panelY, panelW, panelH, minecraft));
        WorldGateMenuProfile.addPreview(this::addRenderableWidget, minecraft, panelX, panelY, panelW, 142,
                () -> minecraft.setScreen(new WardrobeScreen(this, WardrobeScreen.Tab.COSMETICS)));
        WorldGateMenuProfile.refreshOwned();

        int railW = Math.min(156, Math.max(138, width / 9));
        int railX = width - railW - 18;
        int railHeight = 8 * 25 + 7 * 4;
        int railY = Math.max(120, Math.min(height - railHeight - 34, height / 2 - railHeight / 2));
        worldgate$addRail(railX, railY, railW);

        addRenderableWidget(new IconButton(width - 76, 18, 34,
                IconButton.Icon.BELL,
                () -> minecraft.setScreen(new NotificationsScreen(this))));
        addRenderableWidget(new IconButton(width - 38, 18, 34,
                IconButton.Icon.MAILBOX,
                () -> minecraft.setScreen(new ClaimCenterScreen(this))));

        worldgate$replaceModsIcon(top);
    }

    private void worldgate$addRail(int x, int y, int width) {
        int h = 25;
        int gap = 4;
        addRenderableWidget(new WorldGateButton(x, y, width, h, Component.literal("WorldGate"),
                () -> minecraft.setScreen(new WorldGateScreen(this)), 0xFF67D8FF, WorldGateButton.Icon.FEATURES));
        addRenderableWidget(new WorldGateButton(x, y + (h + gap), width, h, Component.literal("Host"),
                () -> minecraft.setScreen(new WorldGateScreen(this)), 0xFF67D8FF, WorldGateButton.Icon.HOST));
        addRenderableWidget(new WorldGateButton(x, y + 2 * (h + gap), width, h, Component.literal("Social"),
                () -> minecraft.setScreen(new FriendsScreen(this)), 0xFF73E0A1, WorldGateButton.Icon.SOCIAL));
        addRenderableWidget(new WorldGateButton(x, y + 3 * (h + gap), width, h, Component.literal("Wardrobe"),
                () -> minecraft.setScreen(new WardrobeScreen(this, WardrobeScreen.Tab.COSMETICS)), 0xFFFFB86B, WorldGateButton.Icon.WARDROBE));
        addRenderableWidget(new WorldGateButton(x, y + 4 * (h + gap), width, h, Component.literal("Features"),
                () -> minecraft.setScreen(new WorldGateScreen(this)), 0xFF67D8FF, WorldGateButton.Icon.FEATURES));
        addRenderableWidget(new WorldGateButton(x, y + 5 * (h + gap), width, h, Component.literal("Pictures"),
                () -> minecraft.setScreen(new PicturesScreen(this)), 0xFFBDA6FF, WorldGateButton.Icon.PICTURES));
        addRenderableWidget(new WorldGateButton(x, y + 6 * (h + gap), width, h, Component.literal("Settings"),
                () -> minecraft.setScreen(new SettingsScreen(this)), 0xFF9CA9B8, WorldGateButton.Icon.SETTINGS));
        addRenderableWidget(new WorldGateButton(x, y + 7 * (h + gap), width, h, Component.literal("Account"),
                () -> minecraft.setScreen(new EliteProfileScreen(this)), 0xFFFFD36B, WorldGateButton.Icon.ACCOUNT));
    }

    private void worldgate$replaceModsIcon(int top) {
        Button mods = null;
        for (var child : children()) {
            if (child instanceof Button button && button.visible
                    && "Mods".equalsIgnoreCase(button.getMessage().getString())) {
                mods = button;
                break;
            }
        }
        if (mods == null) return;

        int size = Math.max(28, Math.min(44, mods.getHeight()));
        int x = mods.getRight() + 8;
        if (x + size > width - 12) x = Math.max(12, width - size - 12);
        int y = mods.getY() + Math.max(0, (mods.getHeight() - size) / 2);
        addRenderableWidget(new IconButton(x, y, size, IconButton.Icon.SETTINGS,
                () -> minecraft.setScreen(new SettingsScreen(this))));
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