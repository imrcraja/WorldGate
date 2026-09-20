package com.rcraja.worldgate.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class WorldGateKeybinds {

    private WorldGateKeybinds() {
    }

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
                            "key.worldgate.emote",
                            InputConstants.Type.KEYSYM,
                            InputConstants.KEY_G,
                            CATEGORY
                    )
            );

    private static final String[] EMOTES = {
            "wave",
            "dance",
            "sit",
            "cheer"
    };

    private static int emoteIndex = 0;

    public static void register() {

        ClientTickEvents.END_CLIENT_TICK.register(
                WorldGateKeybinds::onClientTick
        );
    }

    private static void onClientTick(
            Minecraft minecraft
    ) {

        while (WORLDGATE.consumeClick()) {

            if (minecraft.player == null) {
                return;
            }

            String room =
                    WorldGateModClient.CURRENT_ROOM_CODE;

            if (room == null
                    || room.isBlank()) {

                minecraft.player.sendSystemMessage(
                        Component.literal(
                                "WorldGate: join a room first."
                        )
                );

                return;
            }

            String emote =
                    EMOTES[emoteIndex];

            emoteIndex =
                    (emoteIndex + 1)
                            % EMOTES.length;

            WorldGateModClient.EXECUTOR.submit(
                    () ->
                            WorldGateModClient.EMOTE_MANAGER
                                    .sendEmote(
                                            room,
                                            emote
                                    )
            );
        }
    }
}
