package com.rcraja.worldgate.mixin;

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

/**
 * Same fix as TitleScreenMixin: leave the vanilla pause menu buttons
 * (Back to Game, Advancements, Statistics, Options, Open to LAN,
 * Save and Quit to Title) in their default position and style, and only
 * add WorldGate's side panel with a clean, vanilla-matching button look.
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void worldgate$modernize(CallbackInfo ci) {
        if (this.minecraft == null || width < 720) return;

        // Vanilla pause menu buttons are left completely untouched here.

        int previewSize = 110;
        int previewX = Math.max(8, width - previewSize - 18);
        int previewY = 40;

        PlayerSkinWidget playerWidget = new PlayerSkinWidget(
                previewSize,
                previewSize + 30,
                this.minecraft.getEntityModels(),
                () -> this.minecraft.playerSkinRenderCache()
                        .getOrDefault(ResolvableProfile.createUnresolved(this.minecraft.getUser().getProfileId()))
                        .playerSkin()
        );
        playerWidget.setPosition(previewX, previewY);
        this.addRenderableWidget(playerWidget);

        int sideW = Math.min(150, width / 6);
        int sideX = width - sideW - 18;
        int bh = 20;
        int gap = 4;
        int sideY = previewY + previewSize + 30 + 14;

        addRenderableWidget(Button.builder(Component.literal("Host"),
                b -> this.minecraft.setScreen(new WorldGateScreen(this)))
                .bounds(sideX, sideY, sideW, bh).build());
        addRenderableWidget(Button.builder(Component.literal("Social"),
                b -> this.minecraft.setScreen(new FriendsScreen(this)))
                .bounds(sideX, sideY + (bh + gap), sideW, bh).build());
        addRenderableWidget(Button.builder(Component.literal("Wardrobe"),
                b -> this.minecraft.setScreen(new WardrobeScreen(this, WardrobeScreen.Tab.COSMETICS)))
                .bounds(sideX, sideY + (bh + gap) * 2, sideW, bh).build());
        addRenderableWidget(Button.builder(Component.literal("Features"),
                b -> this.minecraft.setScreen(new ClaimCenterScreen(this)))
                .bounds(sideX, sideY + (bh + gap) * 3, sideW, bh).build());
        addRenderableWidget(Button.builder(Component.literal("Settings"),
                b -> this.minecraft.setScreen(new SettingsScreen(this)))
                .bounds(sideX, sideY + (bh + gap) * 4, sideW, bh).build());
        addRenderableWidget(Button.builder(Component.literal("Account"),
                b -> this.minecraft.setScreen(new EliteProfileScreen(this)))
                .bounds(sideX, sideY + (bh + gap) * 5, sideW, bh).build());
    }
}
