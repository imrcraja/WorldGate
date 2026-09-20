package com.rcraja.worldgate.mixin;

import com.rcraja.worldgate.client.gui.WorldGateScreen;

import net.minecraft.client.gui.DrawContext;
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

    @Inject(
            method = "init",
            at = @At("TAIL")
    )
    private void worldgate$addButton(
            CallbackInfo ci
    ) {

        int centerX =
                this.width / 2;

        int y =
                this.height / 4 + 96;

        this.addRenderableWidget(
                Button.builder(
                        net.minecraft.network.chat.Component.literal(
                                "WorldGate"
                        ),
                        btn -> this.minecraft.setScreen(
                                new WorldGateScreen(this)
                        )
                )
                .bounds(
                        centerX - 100,
                        y,
                        200,
                        20
                )
                .build()
        );
    }
}
