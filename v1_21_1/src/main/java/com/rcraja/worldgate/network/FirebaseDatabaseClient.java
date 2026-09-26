package com.rcraja.worldgate.network;

import com.rcraja.worldgate.Constants;
import com.rcraja.worldgate.WorldGateMod;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Thin wrapper over the Realtime Database REST API
 * (https://<DB_URL>/path.json). No Minecraft classes here, so it is
 * shared by every version module.
 */
public class FirebaseDatabaseClient {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private final String idToken;

    public FirebaseDatabaseClient(String idToken) {
        this.idToken = idToken;
    }

    private String url(String path) {
        return Constants.FIREBASE_DATABASE_URL.replaceAll("/$", "") + path + ".json?auth=" + idToken;
    }

    public String get(String path) {
        return request("GET", path, null);
    }

    public String put(String path, String jsonBody) {
        return request("PUT", path, jsonBody);
    }

    public String patch(String path, String jsonBody) {
        return request("PATCH", path, jsonBody);
    }

    /** Firebase "push" semantics -- generates a unique key, returns {"name": "-Nx..."} */
    public String post(String path, String jsonBody) {
        return request("POST", path, jsonBody);
    }

    public void delete(String path) {
        request("DELETE", path, null);
    }

    private String request(String method, String path, String body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url(path)));
            switch (method) {
                case "GET" -> builder.GET();
                case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(body));
                case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body));
                case "PATCH" -> builder.method("PATCH", HttpRequest.BodyPublishers.ofString(body));
                case "DELETE" -> builder.DELETE();
                default -> throw new IllegalArgumentException(method);
            }
            HttpResponse<String> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            WorldGateMod.LOGGER.error("WorldGate database request failed: {} {}", method, path, e);
            return null;
        }
    }
}
