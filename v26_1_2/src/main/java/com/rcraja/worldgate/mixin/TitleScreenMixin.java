package com.rcraja.worldgate.mixin;

import com.rcraja.worldgate.client.gui.WorldGateScreen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(
            net.minecraft.network.chat.Component title
    ) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void worldgate$addButton(CallbackInfo ci) {
        Button modsButton = null;

        for (var child : this.children()) {
            if (child instanceof Button button) {
                String label = button.getMessage().getString();
                if ("Mods".equalsIgnoreCase(label)
                        || label.toLowerCase().contains("mod menu")) {
                    modsButton = button;
                    break;
                }
            }
        }

        int x;
        int y;

        if (modsButton != null) {
            x = modsButton.getX();
            y = Math.max(8, modsButton.getY() - 24);
        } else {
            x = this.width / 2 - 100;
            y = this.height - 28;
        }

        this.addRenderableWidget(
                Button.builder(
                        net.minecraft.network.chat.Component.literal("WorldGate"),
                        btn -> this.minecraft.setScreen(
                                new WorldGateScreen(this)
                        )
                )
                .bounds(x, y, 200, 20)
                .build()
        );
    }
}
