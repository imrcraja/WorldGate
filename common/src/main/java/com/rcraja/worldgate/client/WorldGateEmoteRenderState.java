package com.rcraja.worldgate.client;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Carries the player identity between the 26.1.2 avatar extraction and model phases.
 * The vanilla 26.1.2 AvatarRenderState does not expose the render-data accessors used
 * by some newer render-state APIs, so WorldGate keeps this small client-side association.
 */
public final class WorldGateEmoteRenderState {
    private static final Map<AvatarRenderState, UUID> PLAYER_IDS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private WorldGateEmoteRenderState() {}

    public static void setPlayerId(AvatarRenderState state, UUID playerId) {
        if (state != null) {
            PLAYER_IDS.put(state, playerId);
        }
    }

    public static UUID getPlayerId(AvatarRenderState state) {
        return state == null ? null : PLAYER_IDS.get(state);
    }
}
