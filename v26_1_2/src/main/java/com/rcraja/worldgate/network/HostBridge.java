package com.rcraja.worldgate.network;

import com.rcraja.worldgate.WorldGateMod;

/**
 * Host-side networking bridge.
 *
 * This class will connect the Minecraft IntegratedServer to the
 * WorldGate relay after the world is published.
 *
 * The actual relay protocol is intentionally added in the next step.
 */
public final class HostBridge {

    private HostBridge() {
    }

    private static volatile boolean running = false;
    private static volatile int minecraftPort = -1;

    public static boolean start(int port) {
        if (running) {
            return true;
        }

        if (port <= 0 || port > 65535) {
            WorldGateMod.LOGGER.error("WorldGate: invalid host port {}", port);
            return false;
        }

        minecraftPort = port;
        running = true;

        WorldGateMod.LOGGER.info(
                "WorldGate host bridge prepared for Minecraft port {}",
                port
        );

        return true;
    }

    public static void stop() {
        if (!running) {
            return;
        }

        running = false;
        minecraftPort = -1;

        WorldGateMod.LOGGER.info("WorldGate host bridge stopped.");
    }

    public static boolean isRunning() {
        return running;
    }

    public static int getMinecraftPort() {
        return minecraftPort;
    }
}
