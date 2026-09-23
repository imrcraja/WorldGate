package com.rcraja.worldgate.network;

import com.rcraja.worldgate.Constants;
import com.rcraja.worldgate.WorldGateMod;
import com.rcraja.worldgate.client.WorldGateModClient;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public final class RelayBridge {

    private RelayBridge() {}

    private static final Object LOCK = new Object();

    private static volatile WebSocket webSocket;
    private static volatile Socket tcpSocket;
    private static volatile ServerSocket playerServer;
    private static volatile boolean running;
    private static volatile boolean connected;


    public static boolean startHost(String roomCode, int minecraftPort) {
        synchronized (LOCK) {
            if (running) {
                return connected;
            }

            if (!IntegrityGuard.verifyLocalRelease()) {
                return false;
            }

            if (roomCode == null || roomCode.isBlank()) {
                return false;
            }

            if (minecraftPort <= 0 || minecraftPort > 65535) {
                return false;
            }

            running = true;
            connected = false;
        }

        Thread thread = new Thread(() -> {
            try {
                WebSocket ws = connectWebSocket("host", roomCode);
                if (ws == null) {
                    stop();
                    return;
                }

                WorldGateMod.LOGGER.info(
                        "WorldGate relay host connected for room {}",
                        roomCode
                );

                if (!waitForConnection(15)) {
                    WorldGateMod.LOGGER.error(
                            "WorldGate relay host connection timed out."
                    );
                    stop();
                    return;
                }

                Socket socket = new Socket("127.0.0.1", minecraftPort);
                tcpSocket = socket;

                bridge(socket, ws);

            } catch (Exception e) {
                WorldGateMod.LOGGER.error(
                        "WorldGate host relay failed", e
                );
                stop();
            }
        }, "WorldGate-Relay-Host");

        thread.setDaemon(true);
        thread.start();

        return true;
    }

    public static int startPlayer(String roomCode) {
        synchronized (LOCK) {
            if (running) {
                return playerServer != null ? playerServer.getLocalPort() : -1;
            }

            if (!IntegrityGuard.verifyLocalRelease()) {
                return -1;
            }

            if (roomCode == null || roomCode.isBlank()) {
                return -1;
            }

            try {
                ServerSocket server = new ServerSocket(0, 1,
                        java.net.InetAddress.getLoopbackAddress());

                playerServer = server;
                running = true;
                connected = false;

                Thread thread = new Thread(
                        () -> playerThread(roomCode, server),
                        "WorldGate-Relay-Player"
                );

                thread.setDaemon(true);
                thread.start();

                return server.getLocalPort();

            } catch (Exception e) {
                WorldGateMod.LOGGER.error(
                        "WorldGate player local proxy failed", e
                );
                running = false;
                return -1;
            }
        }
    }

    private static void playerThread(
            String roomCode,
            ServerSocket server
    ) {
        try {
            WebSocket ws = connectWebSocket("player", roomCode);

            if (ws == null) {
                stop();
                return;
            }

            WorldGateMod.LOGGER.info(
                    "WorldGate relay player connected for room {}",
                    roomCode
            );

            Socket socket = server.accept();
            tcpSocket = socket;

            if (!waitForConnection(15)) {
                WorldGateMod.LOGGER.error(
                        "WorldGate relay player connection timed out."
                );
                stop();
                return;
            }

            bridge(socket, ws);

        } catch (Exception e) {
            WorldGateMod.LOGGER.error(
                    "WorldGate player relay failed", e
            );
            stop();
        }
    }

    private static WebSocket connectWebSocket(
            String role,
            String roomCode
    ) {
        try {
            HttpClient client = HttpClient.newHttpClient();

            RelayListener listener = new RelayListener();

            WebSocket ws = client.newWebSocketBuilder()
                    .buildAsync(
                            URI.create(Constants.RELAY_WS_URL),
                            listener
                    )
                    .join();

            String uid = WorldGateModClient.SESSION.uid();
            String modSha256 = IntegrityGuard.currentArtifactSha256();
            String handshake =
                    "{\"protocol\":2,\"role\":\"" + role +
                    "\",\"room\":\"" + escape(roomCode) +
                    "\",\"uid\":\"" + escape(uid == null ? "" : uid) +
                    "\",\"modSha256\":\"" + escape(modSha256) + "\"}";

            ws.sendText(handshake, true);

            webSocket = ws;

            return ws;

        } catch (Exception e) {
            WorldGateMod.LOGGER.error(
                    "WorldGate WebSocket connection failed", e
            );
            return null;
        }
    }

    private static void bridge(
            Socket socket,
            WebSocket ws
    ) throws Exception {

        InputStream input = socket.getInputStream();
        OutputStream output = socket.getOutputStream();

        Thread tcpToWebSocket = new Thread(() -> {
            byte[] buffer = new byte[16384];

            try {
                int read;

                while (running && (read = input.read(buffer)) != -1) {
                    ByteBuffer data =
                            ByteBuffer.wrap(buffer, 0, read);

                    ws.sendBinary(data, true).join();
                }

            } catch (Exception e) {
                if (running) {
                    WorldGateMod.LOGGER.warn(
                            "WorldGate TCP -> WebSocket stopped", e
                    );
                }
            } finally {
                stop();
            }
        }, "WorldGate-TCP-To-WS");

        tcpToWebSocket.setDaemon(true);
        tcpToWebSocket.start();

        WorldGateMod.LOGGER.info(
                "WorldGate TCP/WebSocket bridge active."
        );
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private static void writeTcp(ByteBuffer data) {
        Socket socket = tcpSocket;

        if (socket == null || socket.isClosed()) {
            return;
        }

        try {
            OutputStream output = socket.getOutputStream();

            ByteBuffer copy = data.slice();
            byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);

            output.write(bytes);
            output.flush();

        } catch (Exception e) {
            if (running) {
                WorldGateMod.LOGGER.warn(
                        "WorldGate WebSocket -> TCP failed", e
                );
            }

            stop();
        }
    }

    private static boolean waitForConnection(int seconds) {
        long deadline = System.currentTimeMillis()
                + (seconds * 1000L);

        while (running
                && !connected
                && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return running && connected;
    }

    public static boolean isRunning() {
        return running;
    }

    public static boolean isConnected() {
        return connected;
    }

    public static void stop() {
        synchronized (LOCK) {
            running = false;
            connected = false;
        }

        try {
            if (webSocket != null) {
                webSocket.sendClose(
                        WebSocket.NORMAL_CLOSURE,
                        "WorldGate closed"
                );
            }
        } catch (Exception ignored) {
        }

        try {
            if (tcpSocket != null) {
                tcpSocket.close();
            }
        } catch (Exception ignored) {
        }

        try {
            if (playerServer != null) {
                playerServer.close();
            }
        } catch (Exception ignored) {
        }

        webSocket = null;
        tcpSocket = null;
        playerServer = null;
    }

    private static final class RelayListener
            implements WebSocket.Listener {

        @Override
        public void onOpen(WebSocket webSocket) {
            WebSocket.Listener.super.onOpen(webSocket);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(
                WebSocket webSocket,
                CharSequence data,
                boolean last
        ) {
            String message = data.toString();

            if (message.contains("\"type\":\"connected\"")) {
                connected = true;

                WorldGateMod.LOGGER.info(
                        "WorldGate relay pair connected."
                );
            }

            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onBinary(
                WebSocket webSocket,
                ByteBuffer data,
                boolean last
        ) {
            writeTcp(data);
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(
                WebSocket webSocket,
                int statusCode,
                String reason
        ) {
            stop();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(
                WebSocket webSocket,
                Throwable error
        ) {
            WorldGateMod.LOGGER.error(
                    "WorldGate relay WebSocket error", error
            );
            stop();
        }
    }
}
