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
    private static volatile boolean handshakeAccepted;
    private static volatile boolean handshakeRejected;
    private static volatile long lastPongAt;
    private static volatile long lastPingAt;
    private static volatile long rttMillis = -1;


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

                if (!waitForHandshake(15)) {
                    WorldGateMod.LOGGER.error(
                            "WorldGate relay host handshake timed out."
                    );
                    stop();
                    return;
                }

                // The relay may acknowledge the host with "waiting" before the
                // player arrives. Keep the host bridge alive while waiting for
                // the actual pair; only then does Minecraft traffic flow.
                if (!waitForConnection(30 * 60)) {
                    WorldGateMod.LOGGER.error(
                            "WorldGate relay host pairing timed out."
                    );
                    stop();
                    return;
                }

                Socket socket = new Socket("127.0.0.1", minecraftPort);
                socket.setTcpNoDelay(true);
                socket.setKeepAlive(true);
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

                server.setReuseAddress(true);
                playerServer = server;
                running = true;
                connected = false;

                WebSocket ws = connectWebSocket("player", roomCode);
                if (ws == null) {
                    stop();
                    return -1;
                }

                Thread thread = new Thread(
                        () -> playerThread(roomCode, server, ws),
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
            ServerSocket server,
            WebSocket ws
    ) {
        try {
            WorldGateMod.LOGGER.info(
                    "WorldGate relay player connected for room {}",
                    roomCode
            );

            Socket socket = server.accept();
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
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
            HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build();

            RelayListener listener = new RelayListener();
            handshakeAccepted = false;
            handshakeRejected = false;

            WebSocket ws = client.newWebSocketBuilder()
                    .buildAsync(
                            URI.create(Constants.RELAY_WS_URL),
                            listener
                    )
                    .get(5, TimeUnit.SECONDS);

            String uid = WorldGateModClient.SESSION.uid();
            String modSha256 = IntegrityGuard.currentArtifactSha256();
            String handshake =
                    "{\"protocol\":2,\"role\":\"" + role +
                    "\",\"room\":\"" + escape(roomCode) +
                    "\",\"uid\":\"" + escape(uid == null ? "" : uid) +
                    "\",\"modSha256\":\"" + escape(modSha256) + "\"}";

            ws.sendText(handshake, true);

            long deadline = System.currentTimeMillis() + 5000L;
            while (running
                    && !handshakeAccepted
                    && !handshakeRejected
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(25);
            }

            if (!running || handshakeRejected || !handshakeAccepted) {
                try {
                    ws.sendClose(WebSocket.NORMAL_CLOSURE, "handshake timeout");
                } catch (Exception ignored) {
                }
                return null;
            }

            webSocket = ws;
            startLatencyProbe(ws);
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

                    ws.sendBinary(data, true);
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
            // TCP_NODELAY keeps small Minecraft packets from waiting for a flush cycle.\n            output.flush();

        } catch (Exception e) {
            if (running) {
                WorldGateMod.LOGGER.warn(
                        "WorldGate WebSocket -> TCP failed", e
                );
            }

            stop();
        }
    }

    private static boolean waitForHandshake(int seconds) {
        long deadline = System.currentTimeMillis()
                + (seconds * 1000L);

        while (running
                && !handshakeAccepted
                && !handshakeRejected
                && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return running && handshakeAccepted && !handshakeRejected;
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

    public static long getRttMillis() { return rttMillis; }

    private static void startLatencyProbe(WebSocket ws) {
        Thread t = new Thread(() -> {
            while (running && webSocket == ws) {
                try {
                    lastPingAt = System.nanoTime();
                    ws.sendPing(ByteBuffer.wrap(new byte[] { 87, 71, 80, 49 }));
                    Thread.sleep(5000L);
                } catch (Exception e) {
                    return;
                }
            }
        }, "WorldGate-Relay-Latency");
        t.setDaemon(true);
        t.start();
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
            handshakeAccepted = false;
            handshakeRejected = false;
            rttMillis = -1;
            lastPingAt = 0;
            lastPongAt = 0;
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

            if (message.contains("\"type\":\"error\"")
                    || message.contains("\"type\":\"full\"")) {
                handshakeRejected = true;
            }

            if (message.contains("\"code\":\"MOD_INTEGRITY_REJECTED\"")) {
                IntegrityGuard.disable("official mod integrity hash was rejected by the relay");
                handshakeRejected = true;
                stop();
                webSocket.request(1);
                return CompletableFuture.completedFuture(null);
            }

            if (message.contains("\"type\":\"waiting\"")
                    || message.contains("\"type\":\"connected\"")) {
                handshakeAccepted = true;
            }

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
        public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {\n            long sent = lastPingAt;\n            if (sent > 0) rttMillis = Math.max(0L, (System.nanoTime() - sent) / 1_000_000L);\n            lastPongAt = System.currentTimeMillis();\n            webSocket.request(1);\n            return CompletableFuture.completedFuture(null);\n        }\n\n        @Override\n        public CompletionStage<?> onBinary(
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
