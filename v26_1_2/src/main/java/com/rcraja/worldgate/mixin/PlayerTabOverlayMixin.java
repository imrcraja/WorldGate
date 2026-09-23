package com.rcraja.worldgate.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.rcraja.worldgate.client.gui.PingColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Adds the small white WorldGate mark to the local player's Tab-list name
 * without changing Minecraft's existing Tab-list background or avatar layout,
 * and keeps the existing latency color indicator.
 */
@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {

    private static final int WORLDGATE_WHITE = 0xFFFFFFFF;
    private static final String WORLDGATE_MARK = "\uE000 ";
    private static final Identifier WORLDGATE_FONT = Identifier.of("worldgate", "worldgate_tab");

    @ModifyReturnValue(method = "getNameForDisplay", at = @At("RETURN"))
    private Component worldgate$decorateName(Component original, PlayerInfo info) {
        int ping = info.getLatency();
        PingColor color = PingColor.forPing(ping);

        Component decorated = Component.literal("")
                .append(original)
                .append(Component.literal(" " + ping + "ms")
                        .withStyle(style -> style.withColor(color.color)));

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && minecraft.player.getUUID().equals(info.getProfile().id())) {
            return Component.literal(WORLDGATE_MARK)
                    .withStyle(style -> style.withColor(WORLDGATE_WHITE).withFont(Style.DEFAULT_FONT))
                    .append(decorated);
        }

        return decorated;
    }
}
