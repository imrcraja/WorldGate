package com.rcraja.worldgate.mixin;

import com.rcraja.worldgate.client.gui.WorldGateScreen;

import net.minecraft.client.gui.components.Button;
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
        Button bottomButton = null;

        for (var child : this.children()) {
            if (child instanceof Button button) {
                if (bottomButton == null || button.getY() > bottomButton.getY()) {
                    bottomButton = button;
                }
            }
        }

        int x = this.width / 2 - 100;
        int y;

        if (bottomButton != null) {
            int below = bottomButton.getY() + bottomButton.getHeight() + 4;
            int above = bottomButton.getY() - 24;

            if (below + 20 <= this.height - 8) {
                y = below;
            } else {
                y = Math.max(8, above);
            }
        } else {
            y = this.height / 2 + 70;
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
