package com.rcraja.worldgate.mixin;

import com.rcraja.worldgate.client.WorldGateEmoteRenderState;
import com.rcraja.worldgate.network.EmoteAnimationState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies lightweight procedural WorldGate emotes after vanilla player posing.
 * Vanilla setupAnim resets the model each frame, so the effect is naturally
 * transient and does not persist after the emote expires.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin extends HumanoidModel<AvatarRenderState> {
    protected PlayerModelMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void worldgate$applyEmote(AvatarRenderState state, CallbackInfo ci) {
        java.util.UUID playerId = WorldGateEmoteRenderState.getPlayerId(state);
        if (playerId == null) {
            return;
        }

        EmoteAnimationState.Active active = EmoteAnimationState.get(playerId);
        if (active == null) {
            return;
        }

        float progress = active.progress();
        float envelope = (float) Math.sin(Math.PI * progress);
        float wave = (float) Math.sin(progress * Math.PI * 2.0f);
        String name = active.name();

        switch (name) {
            case "wave" -> {
                this.rightArm.xRot -= 0.75f * envelope;
                this.rightArm.zRot -= 0.55f * envelope;
                this.rightArm.yRot += 0.20f * wave * envelope;
            }
            case "dance" -> {
                this.body.zRot += 0.16f * wave * envelope;
                this.leftArm.xRot += 0.45f * wave * envelope;
                this.rightArm.xRot -= 0.45f * wave * envelope;
                this.leftLeg.xRot -= 0.28f * wave * envelope;
                this.rightLeg.xRot += 0.28f * wave * envelope;
            }
            case "sit" -> {
                this.body.xRot += 0.12f * envelope;
                this.leftLeg.xRot += 0.95f * envelope;
                this.rightLeg.xRot += 0.95f * envelope;
                this.leftArm.xRot -= 0.12f * envelope;
                this.rightArm.xRot -= 0.12f * envelope;
            }
            case "cheer" -> {
                this.leftArm.xRot -= 1.65f * envelope;
                this.rightArm.xRot -= 1.65f * envelope;
                this.leftArm.zRot += 0.18f * envelope;
                this.rightArm.zRot -= 0.18f * envelope;
            }
            default -> {
                // EmoteManager validates names before they reach this state.
            }
        }
    }
}
