package com.rcraja.worldgate.client;

import net.minecraft.client.util.InputUtil;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;

import org.lwjgl.glfw.GLFW;

public final class WorldGateKeybinds {
    private WorldGateKeybinds() {}

    private static final KeyBinding WORLDGATE =
            KeyBindingHelper.registerKeyBinding(
                    new KeyBinding(
                            "key.worldgate.emote",
                            InputUtil.Type.KEYSYM,
                            GLFW.GLFW_KEY_G,
                            "category.worldgate"
                    )
            );

    private static final String[] EMOTES = {"wave", "dance", "sit", "cheer"};
    private static int emoteIndex = 0;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(WorldGateKeybinds::onClientTick);
    }

    private static void onClientTick(MinecraftClient client) {
        while (WORLDGATE.wasPressed()) {
            if (client.player == null) return;

            String room = WorldGateModClient.CURRENT_ROOM_CODE;
            if (room == null || room.isBlank()) {
                client.player.sendMessage(
                        Text.literal("WorldGate: join a room first."),
                        false
                );
                return;
            }

            String emote = EMOTES[emoteIndex];
            emoteIndex = (emoteIndex + 1) % EMOTES.length;

            WorldGateModClient.EXECUTOR.submit(
                    () -> WorldGateModClient.EMOTE_MANAGER.sendEmote(room, emote)
            );
        }
    }
}
