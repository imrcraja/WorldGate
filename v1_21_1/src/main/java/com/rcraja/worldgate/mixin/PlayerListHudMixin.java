package com.rcraja.worldgate.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.rcraja.worldgate.client.gui.PingColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Adds the small white WorldGate mark to the local player's Tab-list name
 * without changing Minecraft's existing Tab-list background or avatar layout,
 * and keeps the existing latency color indicator.
 */
@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {

    private static final int WORLDGATE_WHITE = 0xFFFFFF;
    private static final String WORLDGATE_MARK = "◈ ";

    @ModifyReturnValue(method = "getPlayerName", at = @At("RETURN"))
    private Text worldgate$decorateName(Text original, PlayerListEntry entry) {
        int ping = entry.getLatency();
        PingColor color = PingColor.forPing(ping);

        Text decorated = Text.empty()
                .append(original)
                .append(Text.literal(" " + ping + "ms")
                        .styled(style -> style.withColor(color.color)));

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null
                && client.player.getUuid().equals(entry.getProfile().getId())) {
            return Text.literal(WORLDGATE_MARK)
                    .styled(style -> style.withColor(WORLDGATE_WHITE))
                    .append(decorated);
        }

        return decorated;
    }
}
