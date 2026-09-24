package com.rcraja.worldgate.client;

import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;

import java.util.UUID;

/** Render-state key used to carry the player identity into the 26.1.2 model phase. */
public final class WorldGateEmoteRenderState {
    public static final ContextKey<UUID> PLAYER_ID =
            new ContextKey<>(Identifier.parse("worldgate:emote_player_id"));

    private WorldGateEmoteRenderState() {}
}
