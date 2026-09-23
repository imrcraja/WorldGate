package com.rcraja.worldgate.network;

import com.rcraja.worldgate.Constants;
import com.rcraja.worldgate.WorldGateMod;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Server-authoritative WorldGate API client used by the 26.1.2 integration.
 * Identity is carried by the Firebase ID token; the backend remains the
 * authority for Elite entitlement and published public badges.
 */
public final class BackendClient {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private BackendClient() {}

    public static String eliteProfile(FirebaseSession session, String uid) {
        if (session == null || !session.isReady() || session.idToken() == null) {
            return null;
        }
        String body = "{"uid":"" + escape(uid) + ""}";
        return request(
                "POST",
                Constants.BACKEND_BASE_URL + "/v1/elite/profile",
                session.idToken(),
                body
        );
    }

    public static String publicEliteProfile(FirebaseSession session, String uid) {
        if (session == null || !session.isReady() || session.idToken() == null) {
            return null;
        }
        String body = "{"uids":["" + escape(uid) + ""]}";
        return request(
                "POST",
                Constants.BACKEND_BASE_URL + "/v1/elite/public-profiles",
                session.idToken(),
                body
        );
    }

    private static String request(String method, String url, String token, String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response =
                    CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                WorldGateMod.LOGGER.warn(
                        "WorldGate backend returned HTTP {} for {}",
                        response.statusCode(),
                        url
                );
                return null;
            }

            return response.body();
        } catch (Exception e) {
            WorldGateMod.LOGGER.warn("WorldGate backend request failed: {}", url, e);
            return null;
        }
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace(""", "\\"");
    }
}
