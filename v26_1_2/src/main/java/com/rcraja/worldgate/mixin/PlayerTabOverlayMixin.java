package com.rcraja.worldgate.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.rcraja.worldgate.client.gui.PingColor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Colors each player's Tab-list ping: white -> yellow -> orange -> red.
 *
 * Minecraft 26.1.2 uses "getNameForDisplay" for the player name component.
 */
@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {

    @ModifyReturnValue(method = "getNameForDisplay", at = @At("RETURN"))
    private Component worldgate$colorPingName(Component original, PlayerInfo info) {
        int ping = info.getLatency();
        PingColor color = PingColor.forPing(ping);
        return Component.literal("")
                .append(original)
                .append(Component.literal(" " + ping + "ms")
                        .withStyle(style -> style.withColor(color.color)));
    }
}
