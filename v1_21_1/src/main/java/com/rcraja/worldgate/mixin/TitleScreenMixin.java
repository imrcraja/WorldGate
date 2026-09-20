package com.rcraja.worldgate.mixin;

import com.rcraja.worldgate.client.gui.WorldGateScreen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void worldgate$addButton(CallbackInfo ci) {
        ButtonWidget modsButton = null;

        // Mod Menu creates its button during TitleScreen.init(). When present,
        // anchor WorldGate to it instead of using a hard-coded screen position.
        // This keeps the layout correct across resolutions and Mod Menu versions.
        for (var child : this.children()) {
            if (child instanceof ButtonWidget button) {
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
