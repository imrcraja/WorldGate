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

// NOTE: GameMenuScreen is Yarn's name for the Escape/pause menu (Mojang
// calls it PauseScreen). If "init" isn't the right injection target here,
// decompile GameMenuScreen via the loom genSources task to confirm.
@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {

    protected GameMenuScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void worldgate$addButton(CallbackInfo ci) {
        this.addDrawableChild(
            ButtonWidget.builder(Text.translatable("worldgate.button.open"), btn ->
                this.client.setScreen(new WorldGateScreen(this))
            )
            .dimensions(this.width / 2 - 100, 10, 200, 20)
            .build()
        );
    }
}
