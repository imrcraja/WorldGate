package com.rcraja.worldgate.network;

import com.rcraja.worldgate.WorldGateMod;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class FirebaseStreamClient {

    private final HttpClient client =
            HttpClient.newHttpClient();

    private volatile boolean running = false;

    private volatile String databaseUrl;
    private volatile String path;
    private volatile String idToken;
    private volatile Consumer<String> onData;

    private Thread thread;

    public synchronized void listen(
            String databaseUrl,
            String path,
            String idToken,
            Consumer<String> onData
    ) {

        stop();

        this.databaseUrl = databaseUrl;
        this.path = path;
        this.idToken = idToken;
        this.onData = onData;

        running = true;

        thread = new Thread(
                this::runStream,
                "WorldGate-Stream-" + path
        );

        thread.setDaemon(true);
        thread.start();
    }

    private void runStream() {

        while (running) {

            boolean connected = false;

            try {

                String base =
                        databaseUrl.replaceAll("/$", "");

                String url =
                        base
                                + path
                                + ".json?auth="
                                + idToken;

                HttpRequest request =
                        HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .header(
                                        "Accept",
                                        "text/event-stream"
                                )
                                .header(
                                        "Cache-Control",
                                        "no-cache"
                                )
                                .GET()
                                .build();

                HttpResponse<InputStream> response =
                        client.send(
                                request,
                                HttpResponse.BodyHandlers
                                        .ofInputStream()
                        );

                int status =
                        response.statusCode();

                if (status < 200 || status >= 300) {

                    WorldGateMod.LOGGER.warn(
                            "WorldGate Firebase stream returned HTTP {} on {}",
                            status,
                            path
                    );

                    closeQuietly(response.body());

                    waitBeforeReconnect();

                    continue;
                }

                connected = true;

                try (
                        BufferedReader reader =
                                new BufferedReader(
                                        new InputStreamReader(
                                                response.body(),
                                                StandardCharsets.UTF_8
                                        )
                                )
                ) {

                    String line;

                    while (
                            running
                                    && (line =
                                    reader.readLine()) != null
                    ) {

                        if (line.startsWith("data: ")) {

                            Consumer<String> callback =
                                    onData;

                            if (callback != null) {

                                callback.accept(
                                        line.substring(6)
                                );
                            }
                        }
                    }
                }

            } catch (Exception e) {

                if (running) {

                    WorldGateMod.LOGGER.warn(
                            "WorldGate realtime connection lost on {}. Reconnecting...",
                            path
                    );
                }
            }

            if (running) {

                if (connected) {

                    WorldGateMod.LOGGER.info(
                            "WorldGate realtime stream ended on {}. Reconnecting...",
                            path
                    );
                }

                waitBeforeReconnect();
            }
        }
    }

    private void waitBeforeReconnect() {

        try {

            Thread.sleep(3000);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(
            InputStream input
    ) {

        if (input == null) {
            return;
        }

        try {
            input.close();
        } catch (Exception ignored) {
        }
    }

    public synchronized void stop() {

        running = false;

        if (thread != null) {

            thread.interrupt();
            thread = null;
        }
    }
}
