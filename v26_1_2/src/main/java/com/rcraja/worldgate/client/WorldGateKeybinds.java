package com.rcraja.worldgate.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.rcraja.worldgate.client.gui.WorldGateScreen;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class WorldGateKeybinds {

    private WorldGateKeybinds() {}

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(
                    Identifier.fromNamespaceAndPath(
                            "worldgate",
                            "worldgate"
                    )
            );

    public static final KeyMapping WORLDGATE =
            KeyMappingHelper.registerKeyMapping(
                    new KeyMapping(
                            "WorldGate",
                            InputConstants.Type.KEYSYM,
                            InputConstants.KEY_G,
                            CATEGORY
                    )
            );

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(
                WorldGateKeybinds::onClientTick
        );
    }

    private static void onClientTick(Minecraft minecraft) {
        while (WORLDGATE.consumeClick()) {
            if (minecraft.player == null) {
                return;
            }

            minecraft.setScreen(
                    new WorldGateScreen(minecraft.screen)
            );
        }
    }
}
