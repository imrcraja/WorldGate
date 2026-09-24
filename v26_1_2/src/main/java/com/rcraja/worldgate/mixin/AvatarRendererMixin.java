package com.rcraja.worldgate.mixin;

import com.rcraja.worldgate.client.WorldGateEmoteRenderState;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carries the player UUID into the 26.1.2 render state during extraction.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V",
            at = @At("TAIL")
    )
    private void worldgate$storePlayerId(
            AbstractClientPlayer player,
            PlayerRenderState state,
            float partialTick,
            CallbackInfo ci
    ) {
        if (player != null) {
            state.setRenderData(WorldGateEmoteRenderState.PLAYER_ID, player.getUUID());
        }
    }
}
