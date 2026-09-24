package com.rcraja.worldgate.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.rcraja.worldgate.client.gui.PingColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Adds the small white WorldGate mark to the local player's Tab-list name
 * without changing Minecraft's existing Tab-list background or avatar layout,
 * and keeps the existing latency color indicator.
 */
@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {

    @ModifyReturnValue(method = "getNameForDisplay", at = @At("RETURN"))
    private Component worldgate$decorateName(Component original, PlayerInfo info) {
        int ping = Math.max(0, info.getLatency());
        PingColor color = PingColor.forPing(ping);

        /*
         * Keep the vanilla player name untouched. The previous custom-font
         * marker produced [][][] when the font atlas did not contain U+E000,
         * and it also made the local player's entry look different from the
         * rest of the tab list.
         */
        return Component.literal("")
                .append(original)
                .append(Component.literal("  " + ping + " ms")
                        .withStyle(style -> style.withColor(color.color)));
    }

}
