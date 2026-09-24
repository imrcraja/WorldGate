package com.rcraja.worldgate.mixin;

import com.rcraja.worldgate.client.gui.WorldGateScreen;

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
    private void worldgate$addButton(CallbackInfo ci) {
        Button topButton = null;

        for (var child : this.children()) {
            if (child instanceof Button button) {
                if (topButton == null || button.getY() < topButton.getY()) {
                    topButton = button;
                }
            }
        }

        int x = this.width / 2 - 100;
        int y;

        if (topButton != null) {
            x = topButton.getX();
            y = Math.max(8, topButton.getY() - 24);
        } else {
            y = Math.max(8, this.height / 2 - 70);
        }

        if (this.minecraft != null) {
            int previewSize = this.width >= 760 ? 150 : 110;
            int previewX = this.width / 2 + 125;
            if (previewX + previewSize > this.width - 12) {
                previewX = this.width / 2 - 125 - previewSize;
            }
            if (previewX >= 8) {
                PlayerSkinWidget playerWidget = new PlayerSkinWidget(
                        previewSize,
                        previewSize + 34,
                        this.minecraft.getEntityModels(),
                        () -> this.minecraft.playerSkinRenderCache()
                                .getOrDefault(ResolvableProfile.createUnresolved(this.minecraft.getGameProfile()))
                                .playerSkin()
                );
                playerWidget.setPosition(
                        previewX,
                        Math.max(18, this.height / 2 - previewSize / 2 - 10)
                );
                this.addRenderableWidget(playerWidget);
            }
        }

        this.addRenderableWidget(
                Button.builder(
                        Component.literal("WorldGate"),
                        btn -> this.minecraft.setScreen(
                                new WorldGateScreen(this)
                        )
                )
                .bounds(x, y, 200, 20)
                .build()
        );
    }
}
