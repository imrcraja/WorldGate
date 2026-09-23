package com.rcraja.worldgate.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.rcraja.worldgate.client.gui.PingColor;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Colors each player's Tab-list ping: white -> yellow -> orange -> red.
 *
 * NOTE: "getPlayerName" is the Yarn 1.21.1 method name for the Tab-list
 * display text. If the build fails on this class, open PlayerListHud via
 * the loom genSources task and update the method name/signature.
 */
@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {

    @ModifyReturnValue(method = "getPlayerName", at = @At("RETURN"))
    private Text worldgate$colorPingName(Text original, PlayerListEntry entry) {
        int ping = entry.getLatency();
        PingColor color = PingColor.forPing(ping);
        return Text.empty()
                .append(original)
                .append(Text.literal(" " + ping + "ms").styled(style -> style.withColor(color.color)));
    }
}
