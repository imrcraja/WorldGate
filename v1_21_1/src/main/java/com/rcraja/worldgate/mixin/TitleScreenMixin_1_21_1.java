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
        int centerX = this.width / 2;

        this.addDrawableChild(
                ButtonWidget.builder(
                        Text.translatable("worldgate.button.open"),
                        btn -> this.client.setScreen(
                                new WorldGateScreen(this)
                        )
                )
                .dimensions(
                        centerX - 100,
                        this.height / 4 + 96,
                        200,
                        20
                )
                .build()
        );
    }
}
