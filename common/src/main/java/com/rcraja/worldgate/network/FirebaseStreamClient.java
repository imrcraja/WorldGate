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

/**
 * Listens to a Realtime Database path via Firebase's server-sent-events
 * streaming REST API, so new chat/emote/friend-request entries arrive
 * without polling. Runs on its own daemon thread; call stop() when the
 * player leaves the room.
 */
public class FirebaseStreamClient {
    private final HttpClient client = HttpClient.newHttpClient();
    private volatile boolean running = false;
    private Thread thread;

    public void listen(String databaseUrl, String path, String idToken, Consumer<String> onData) {
        stop();
        running = true;
        String base = databaseUrl.replaceAll("/$", "");
        thread = new Thread(() -> {
            try {
                String url = base + path + ".json?auth=" + idToken;
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Accept", "text/event-stream")
                        .GET()
                        .build();

                HttpResponse<InputStream> response =
                        client.send(request, HttpResponse.BodyHandlers.ofInputStream());

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                    String line;
                    while (running && (line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            onData.accept(line.substring(6));
                        }
                    }
                }
            } catch (Exception e) {
                if (running) {
                    WorldGateMod.LOGGER.error("WorldGate stream error on {}", path, e);
                }
            }
        }, "WorldGate-Stream-" + path);
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }
}
