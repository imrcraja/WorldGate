package com.rcraja.worldgate.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.rcraja.worldgate.client.WorldGateSkinCache;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Feeds WorldGate profile skin URLs into the normal Minecraft skin pipeline.
 * No manual skin upload is required from the player.
 */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {
    @Inject(method = "<init>", at = @At("HEAD"))
    private static void worldgate$applyProfileSkin(GameProfile profile, boolean enforcesSecureChat, CallbackInfo ci) {
        if (profile == null || profile.id() == null) return;

        String skinUrl = WorldGateSkinCache.get(profile.id());
        if (skinUrl == null || skinUrl.isBlank()) return;

        try {
            String payload = "{\"textures\":{\"SKIN\":{\"url\":\"" +
                    skinUrl.replace("\\", "\\\\").replace("\"", "\\\"") +
                    "\"}}}";
            String encoded = Base64.getEncoder().encodeToString(
                    payload.getBytes(StandardCharsets.UTF_8)
            );
            profile.properties().put("textures", new Property("textures", encoded));
        } catch (Exception ignored) {
        }
    }
}
