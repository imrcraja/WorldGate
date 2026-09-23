package com.rcraja.worldgate.client;

import com.rcraja.worldgate.WorldGateMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.Registry;

public final class WorldGateSounds {
    private WorldGateSounds() {}

    public static final SoundEvent WORLDGATE_OPEN = register("worldgate_open");
    public static final SoundEvent WORLDGATE_CLOSE = register("worldgate_close");
    public static final SoundEvent WORLD_CONNECT = register("world_connect");
    public static final SoundEvent WORLD_DISCONNECT = register("world_disconnect");
    public static final SoundEvent LOGIN = register("login");
    public static final SoundEvent LOGOUT = register("logout");
    public static final SoundEvent AUTH_SUCCESS = register("auth_success");
    public static final SoundEvent AUTH_FAIL = register("auth_fail");
    public static final SoundEvent CALL_INCOMING = register("call_incoming");
    public static final SoundEvent CALL_OUTGOING = register("call_outgoing");
    public static final SoundEvent CALL_ACCEPT = register("call_accept");
    public static final SoundEvent CALL_END = register("call_end");

    private static SoundEvent register(String path) {
        Identifier id = Identifier.fromNamespaceAndPath(WorldGateMod.MOD_ID, path);
        return Registry.register(
                BuiltInRegistries.SOUND_EVENT,
                id,
                SoundEvent.createVariableRangeEvent(id)
        );
    }

    public static void initialize() {
        WorldGateMod.LOGGER.info("WorldGate sounds registered.");
    }

    public static void play(SoundEvent sound) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        client.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F));
    }

    public static void play(SoundEvent sound, float volume) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        client.getSoundManager().play(SimpleSoundInstance.forUI(sound, volume));
    }
}
