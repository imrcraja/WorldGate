package com.rcraja.worldgate.network;

import com.rcraja.worldgate.WorldGateMod;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public final class HostBridge {

    private HostBridge() {}

    private static volatile boolean running = false;
    private static volatile int minecraftPort = -1;

    public static boolean start(int port) {
        if (running) {
            return true;
        }

        if (port <= 0 || port > 65535) {
            WorldGateMod.LOGGER.error(
                    "WorldGate: invalid host port {}",
                    port
            );
            return false;
        }

        minecraftPort = port;
        running = true;

        WorldGateMod.LOGGER.info(
                "WorldGate host bridge ready on {}:{}",
                getAdvertiseAddress(),
                port
        );

        return true;
    }

    public static void stop() {
        if (!running) {
            return;
        }

        RelayBridge.stop();

        running = false;
        minecraftPort = -1;

        WorldGateMod.LOGGER.info(
                "WorldGate host bridge stopped."
        );
    }

    public static boolean isRunning() {
        return running;
    }

    public static int getMinecraftPort() {
        return minecraftPort;
    }

    public static boolean startRelay(String roomCode) {
        if (!running || minecraftPort <= 0) {
            return false;
        }

        return RelayBridge.startHost(
                roomCode,
                minecraftPort
        );
    }

    public static String getAdvertiseAddress() {
        try {
            Enumeration<NetworkInterface> interfaces =
                    NetworkInterface.getNetworkInterfaces();

            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();

                if (!ni.isUp()
                        || ni.isLoopback()
                        || ni.isVirtual()) {
                    continue;
                }

                Enumeration<InetAddress> addresses =
                        ni.getInetAddresses();

                while (addresses.hasMoreElements()) {
                    InetAddress address =
                            addresses.nextElement();

                    if (address instanceof Inet4Address
                            && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }

        } catch (Exception e) {
            WorldGateMod.LOGGER.warn(
                    "WorldGate: could not detect LAN address",
                    e
            );
        }

        return "127.0.0.1";
    }
}
