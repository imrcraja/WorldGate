package com.rcraja.worldgate.mixin;

import com.rcraja.worldgate.client.WorldGateModClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * WorldGate room lifetime follows the actual Minecraft connection, not the
 * WorldGate menu. Cleanup happens when the world connection closes.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "close", at = @At("HEAD"))
    private void worldgate$onWorldConnectionClosed(CallbackInfo ci) {
        WorldGateModClient.leaveCurrentRoomOnWorldDisconnect();
    }
}
