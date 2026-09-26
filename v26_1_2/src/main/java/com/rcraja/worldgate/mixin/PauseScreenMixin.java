package com.rcraja.worldgate.mixin;

import com.rcraja.worldgate.client.gui.WorldGateButton;
import com.rcraja.worldgate.client.gui.WorldGateScreen;
import com.rcraja.worldgate.client.gui.FriendsScreen;
import com.rcraja.worldgate.client.gui.SettingsScreen;
import com.rcraja.worldgate.client.gui.WardrobeScreen;
import com.rcraja.worldgate.client.gui.EliteProfileScreen;
import com.rcraja.worldgate.client.gui.ClaimCenterScreen;

import java.util.ArrayList;

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

    @Inject(method = "init", at = @At("TAIL"))
    private void worldgate$modernize(CallbackInfo ci) {
        if (this.minecraft == null) return;

        int mainW = Math.max(300, Math.min(520, width / 2));
        int mainX = Math.max(18, width / 2 - mainW / 2 - (width >= 760 ? 100 : 0));
        int top = Math.max(64, height / 2 - 128);
        int step = 34;
        int index = 0;

        for (var child : new ArrayList<>(this.children())) {
            if (!(child instanceof Button button)) continue;
            String label = button.getMessage().getString().trim();
            if (label.isEmpty()) continue;

            button.visible = false;
            int accent = 0xFF67D8FF;
            String lower = label.toLowerCase();
            if (lower.contains("option")) accent = 0xFF9CA9B8;
            else if (lower.contains("save") || lower.contains("disconnect")) accent = 0xFFFF7D91;
            else if (lower.contains("lan")) accent = 0xFF73E0A1;
            else if (lower.contains("advancement") || lower.contains("statistic")) accent = 0xFFBDA6FF;

            addRenderableWidget(new WorldGateButton(mainX, top + index++ * step, mainW, 30,
                    button.getMessage(), event -> button.onClick(event, false), accent));
        }

        int previewSize = width >= 760 ? 150 : 110;
        int previewX = Math.min(width - previewSize - 18, mainX + mainW + 24);
        if (previewX >= 8) {
            PlayerSkinWidget playerWidget = new PlayerSkinWidget(
                    previewSize,
                    previewSize + 34,
                    this.minecraft.getEntityModels(),
                    () -> this.minecraft.playerSkinRenderCache()
                            .getOrDefault(ResolvableProfile.createUnresolved(this.minecraft.getUser().getProfileId()))
                            .playerSkin()
            );
            playerWidget.setPosition(previewX, Math.max(46, top - 12));
            this.addRenderableWidget(playerWidget);
        }

        if (width >= 720) {
            int sideW = Math.min(190, width / 5);
            int sideX = width - sideW - 18;
            int sideY = Math.max(110, top + 10);
            addRenderableWidget(new WorldGateButton(sideX, sideY, sideW, 32,
                    Component.literal("Host"),
                    () -> this.minecraft.setScreen(new WorldGateScreen(this)), 0xFF67D8FF));
            addRenderableWidget(new WorldGateButton(sideX, sideY + 40, sideW, 32,
                    Component.literal("Social"),
                    () -> this.minecraft.setScreen(new FriendsScreen(this)), 0xFF73E0A1));
            addRenderableWidget(new WorldGateButton(sideX, sideY + 80, sideW, 32,
                    Component.literal("Wardrobe"),
                    () -> this.minecraft.setScreen(new WardrobeScreen(this, WardrobeScreen.Tab.COSMETICS)), 0xFFFFB86B));
            addRenderableWidget(new WorldGateButton(sideX, sideY + 120, sideW, 32,
                    Component.literal("Features"),
                    () -> this.minecraft.setScreen(new ClaimCenterScreen(this)), 0xFFBDA6FF));
            addRenderableWidget(new WorldGateButton(sideX, sideY + 160, sideW, 32,
                    Component.literal("Settings"),
                    () -> this.minecraft.setScreen(new SettingsScreen(this)), 0xFF9CA9B8));
            addRenderableWidget(new WorldGateButton(sideX, sideY + 200, sideW, 32,
                    Component.literal("Account"),
                    () -> this.minecraft.setScreen(new EliteProfileScreen(this)), 0xFFFFD36B));
        }

        addRenderableWidget(new WorldGateButton(
                Math.max(18, width / 2 - 100), height - 38, 200, 28,
                Component.literal("WorldGate"),
                () -> this.minecraft.setScreen(new WorldGateScreen(this)),
                0xFF67D8FF));
    }
}
