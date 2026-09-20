package com.rcraja.worldgate.mixin;

import com.rcraja.worldgate.client.gui.WorldGateScreen;

import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {

    protected GameMenuScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void worldgate$addButton(CallbackInfo ci) {
        ButtonWidget bottomButton = null;

        // Put WorldGate into the existing pause-menu flow rather than
        // overlaying an arbitrary fixed position.
        for (var child : this.children()) {
            if (child instanceof ButtonWidget button) {
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

        this.addDrawableChild(
                ButtonWidget.builder(
                        Text.translatable("worldgate.button.open"),
                        btn -> this.client.setScreen(new WorldGateScreen(this))
                )
                .dimensions(x, y, 200, 20)
                .build()
        );
    }
}
