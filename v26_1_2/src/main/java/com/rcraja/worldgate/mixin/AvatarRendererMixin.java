package com.rcraja.worldgate.mixin;

import com.rcraja.worldgate.client.WorldGateEmoteRenderState;
import net.minecraft.client.renderer.entity.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carries the avatar UUID into the render state so PlayerModel can apply
 * WorldGate's networked emote without reaching back into world/entity state
 * during the model animation phase.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL")
    )
    private void worldgate$storePlayerId(
            Avatar avatar,
            AvatarRenderState state,
            float partialTick,
            CallbackInfo ci
    ) {
        if (avatar != null) {
            state.setRenderData(WorldGateEmoteRenderState.PLAYER_ID, avatar.getUUID());
        }
    }
}
