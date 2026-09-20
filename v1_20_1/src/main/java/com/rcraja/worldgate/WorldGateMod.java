package com.rcraja.worldgate;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared entrypoint -- identical on every Minecraft version, since
 * ModInitializer itself never changes between versions. All the
 * version-specific screens/mixins live in each version module and call
 * into this same class/logger.
 */
public class WorldGateMod implements ModInitializer {
    public static final String MOD_ID = "worldgate";
    public static final Logger LOGGER = LoggerFactory.getLogger("WorldGate");

    @Override
    public void onInitialize() {
        LOGGER.info("WorldGate initializing...");
    }
}
