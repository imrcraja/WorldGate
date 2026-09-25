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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(net.minecraft.network.chat.Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void worldgate$modernize(CallbackInfo ci) {
        int mainW = Math.max(260, Math.min(420, width / 2 - 70));
        int mainX = Math.max(18, width / 2 - mainW / 2 - (width >= 760 ? 90 : 0));
        int top = Math.max(92, height / 4);
        int step = 34;
        int mainIndex = 0;
        int bottomIndex = 0;

        for (var child : this.children()) {
            if (!(child instanceof Button button)) continue;
            String label = button.getMessage().getString().trim();
            if (label.isEmpty()) continue;

            button.visible = false;
            String lower = label.toLowerCase();
            int x = mainX;
            int y = top + mainIndex++ * step;
            int w = mainW;
            int h = 30;
            int accent = 0xFF67D8FF;

            if (lower.contains("option")) {
                int half = Math.max(120, (mainW - 8) / 2);
                x = mainX;
                y = top + 4 * step + 10;
                w = half;
                h = 28;
                bottomIndex++;
                accent = 0xFF9CA9B8;
            } else if (lower.contains("quit")) {
                int half = Math.max(120, (mainW - 8) / 2);
                x = mainX + half + 8;
                y = top + 4 * step + 10;
                w = half;
                h = 28;
                bottomIndex++;
                accent = 0xFFFF7D91;
            } else if (lower.contains("mods")) {
                accent = 0xFFBDA6FF;
            } else if (lower.contains("realms") || lower.contains("online")) {
                accent = 0xFF73E0A1;
            }

            addRenderableWidget(new WorldGateButton(x, y, w, h, button.getMessage(),
                    event -> button.onClick(event, false), accent));
        }

        if (this.minecraft != null) {
            int previewSize = width >= 760 ? 150 : 110;
            int previewX = Math.min(width - previewSize - 18, mainX + mainW + 26);
            if (previewX >= 8) {
                PlayerSkinWidget playerWidget = new PlayerSkinWidget(
                        previewSize,
                        previewSize + 34,
                        this.minecraft.getEntityModels(),
                        () -> this.minecraft.playerSkinRenderCache()
                                .getOrDefault(ResolvableProfile.createUnresolved(this.minecraft.getUser().getProfileId()))
                                .playerSkin()
                );
                playerWidget.setPosition(previewX, Math.max(52, top - 16));
                this.addRenderableWidget(playerWidget);
            }

            if (width >= 720) {
                int sideW = Math.min(190, width / 5);
                int sideX = width - sideW - 18;
                int sideY = Math.max(120, top + 34);
                addRenderableWidget(new WorldGateButton(sideX, sideY, sideW, 32,
                        net.minecraft.network.chat.Component.literal("Host"),
                        () -> this.minecraft.setScreen(new WorldGateScreen(this)), 0xFF67D8FF));
                addRenderableWidget(new WorldGateButton(sideX, sideY + 40, sideW, 32,
                        net.minecraft.network.chat.Component.literal("Social"),
                        () -> this.minecraft.setScreen(new FriendsScreen(this)), 0xFF73E0A1));
                addRenderableWidget(new WorldGateButton(sideX, sideY + 80, sideW, 32,
                        net.minecraft.network.chat.Component.literal("Wardrobe"),
                        () -> this.minecraft.setScreen(new WardrobeScreen(this, WardrobeScreen.Tab.COSMETICS)), 0xFFFFB86B));
                addRenderableWidget(new WorldGateButton(sideX, sideY + 120, sideW, 32,
                        net.minecraft.network.chat.Component.literal("Features"),
                        () -> this.minecraft.setScreen(new ClaimCenterScreen(this)), 0xFFBDA6FF));
                addRenderableWidget(new WorldGateButton(sideX, sideY + 160, sideW, 32,
                        net.minecraft.network.chat.Component.literal("Settings"),
                        () -> this.minecraft.setScreen(new SettingsScreen(this)), 0xFF9CA9B8));
                addRenderableWidget(new WorldGateButton(sideX, sideY + 200, sideW, 32,
                        net.minecraft.network.chat.Component.literal("Account"),
                        () -> this.minecraft.setScreen(new EliteProfileScreen(this)), 0xFFFFD36B));
            }

            addRenderableWidget(new WorldGateButton(
                    Math.max(18, width / 2 - 100), height - 42, 200, 28,
                    net.minecraft.network.chat.Component.literal("WorldGate"),
                    () -> this.minecraft.setScreen(new WorldGateScreen(this)),
                    0xFF67D8FF));
        }
    }
}
