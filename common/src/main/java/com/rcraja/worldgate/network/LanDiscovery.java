package com.rcraja.worldgate.network;

import com.rcraja.worldgate.WorldGateMod;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * UDP discovery used only for same-WiFi/hotspot play.
 * It never carries Minecraft traffic; it only finds the host's TCP endpoint.
 */
public final class LanDiscovery {
    public static final int DISCOVERY_PORT = 28765;
    private static final String QUERY_PREFIX = "WORLDGATE_DISCOVER|";
    private static final String RESPONSE_PREFIX = "WORLDGATE_HOST|";

    private static final Object LOCK = new Object();
    private static volatile boolean hostRunning;
    private static volatile DatagramSocket hostSocket;
    private static volatile Thread hostThread;

    private LanDiscovery() {}

    public static void startHost(String roomCode, int minecraftPort, String hostName) {
        stopHost();
        if (roomCode == null || roomCode.isBlank() || minecraftPort <= 0 || minecraftPort > 65535) {
            return;
        }

        try {
            DatagramSocket socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.setBroadcast(true);
            socket.bind(new InetSocketAddress(DISCOVERY_PORT));

            hostSocket = socket;
            hostRunning = true;

            String safeName = sanitize(hostName);
            Thread thread = new Thread(() -> {
                byte[] buffer = new byte[512];

                while (hostRunning && !socket.isClosed()) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);

                        String message = new String(
                                packet.getData(),
                                packet.getOffset(),
                                packet.getLength(),
                                StandardCharsets.UTF_8
                        );

                        if (!message.equals(
                                QUERY_PREFIX + roomCode.trim().toUpperCase()
                        )) {
                            continue;
                        }

                        String response =
                                RESPONSE_PREFIX
                                        + roomCode.trim().toUpperCase()
                                        + "|"
                                        + minecraftPort
                                        + "|"
                                        + safeName;

                        byte[] data = response.getBytes(StandardCharsets.UTF_8);
                        socket.send(new DatagramPacket(
                                data,
                                data.length,
                                packet.getAddress(),
                                packet.getPort()
                        ));
                    } catch (Exception e) {
                        if (hostRunning) {
                            WorldGateMod.LOGGER.debug(
                                    "WorldGate LAN discovery listener stopped.",
                                    e
                            );
                        }
                    }
                }
            }, "WorldGate-LAN-Discovery");

            thread.setDaemon(true);
            hostThread = thread;
            thread.start();

            WorldGateMod.LOGGER.info(
                    "WorldGate LAN discovery active for room {} on UDP {}",
                    roomCode,
                    DISCOVERY_PORT
            );
        } catch (Exception e) {
            WorldGateMod.LOGGER.warn(
                    "WorldGate could not start LAN discovery.",
                    e
            );
            stopHost();
        }
    }

    public static HostInfo discover(String roomCode, int timeoutMillis) {
        if (roomCode == null || roomCode.isBlank()) {
            return null;
        }

        String code = roomCode.trim().toUpperCase();
        int timeout = Math.max(500, Math.min(timeoutMillis, 5000));

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(timeout);

            byte[] query = (QUERY_PREFIX + code).getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(
                    query,
                    query.length,
                    InetAddress.getByName("255.255.255.255"),
                    DISCOVERY_PORT
            ));

            long deadline = System.currentTimeMillis() + timeout;
            byte[] buffer = new byte[512];

            while (System.currentTimeMillis() < deadline) {
                int remaining = (int) Math.max(
                        1L,
                        Math.min(1000L, deadline - System.currentTimeMillis())
                );
                socket.setSoTimeout(remaining);

                try {
                    DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                    socket.receive(response);

                    String message = new String(
                            response.getData(),
                            response.getOffset(),
                            response.getLength(),
                            StandardCharsets.UTF_8
                    );

                    String prefix = RESPONSE_PREFIX + code + "|";
                    if (!message.startsWith(prefix)) {
                        continue;
                    }

                    String[] parts = message.split("\\|", 4);
                    if (parts.length < 4) {
                        continue;
                    }

                    int port = Integer.parseInt(parts[2]);
                    if (port <= 0 || port > 65535) {
                        continue;
                    }

                    return new HostInfo(
                            response.getAddress().getHostAddress(),
                            port,
                            parts[3].isBlank() ? "WorldGate Host" : parts[3]
                    );
                } catch (java.net.SocketTimeoutException ignored) {
                    break;
                }
            }
        } catch (Exception e) {
            WorldGateMod.LOGGER.debug(
                    "WorldGate LAN discovery failed for {}",
                    code,
                    e
            );
        }

        return null;
    }

    public static void stopHost() {
        synchronized (LOCK) {
            hostRunning = false;

            DatagramSocket socket = hostSocket;
            hostSocket = null;
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception ignored) {
                }
            }

            Thread thread = hostThread;
            hostThread = null;
            if (thread != null) {
                thread.interrupt();
            }
        }
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "WorldGate Host";
        }

        return value
                .replace("|", " ")
                .replace("\n", " ")
                .trim();
    }

    public record HostInfo(String address, int port, String hostName) {}
}
