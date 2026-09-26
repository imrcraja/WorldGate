package com.rcraja.worldgate.mixin;

import com.rcraja.worldgate.client.gui.WorldGateScreen;
import com.rcraja.worldgate.client.gui.FriendsScreen;
import com.rcraja.worldgate.client.gui.SettingsScreen;
import com.rcraja.worldgate.client.gui.WardrobeScreen;
import com.rcraja.worldgate.client.gui.EliteProfileScreen;
import com.rcraja.worldgate.client.gui.ClaimCenterScreen;
import com.rcraja.worldgate.client.gui.NotificationsScreen;
import com.rcraja.worldgate.client.gui.IconButton;

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

/**
 * Keeps the vanilla title screen exactly as Mojang ships it (Singleplayer,
 * Multiplayer, Realms, Mods, Options, Quit stay in their default position and
 * style) and only adds WorldGate's extra buttons on the side, plus a mailbox
 * and a notification icon in the top-right corner - matching how a mod like
 * Essential adds its own panel without reskinning the base menu.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void worldgate$modernize(CallbackInfo ci) {
        if (this.minecraft == null) return;

        // Intentionally not touching the vanilla buttons: Minecraft's own
        // init() has already placed Singleplayer/Multiplayer/Realms/Mods/
        // Options/Quit in their default spots with their default look.

        int previewSize = width >= 760 ? 130 : 96;
        int previewX = Math.max(8, width - previewSize - 18);
        int previewY = 40;

        if (width >= 720) {
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

        int iconSize = 20;
        int iconGap = 6;
        int iconY = 8;
        int mailboxX = width - iconSize - 10;
        int bellX = mailboxX - iconSize - iconGap;

        addRenderableWidget(new IconButton(bellX, iconY, iconSize,
                IconButton.Icon.BELL, () -> this.minecraft.setScreen(new NotificationsScreen(this))));
        addRenderableWidget(new IconButton(mailboxX, iconY, iconSize,
                IconButton.Icon.MAILBOX, () -> this.minecraft.setScreen(new ClaimCenterScreen(this))));
    }
}
