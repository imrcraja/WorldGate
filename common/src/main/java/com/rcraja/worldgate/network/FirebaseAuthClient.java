package com.rcraja.worldgate.network;

import com.rcraja.worldgate.Constants;
import com.rcraja.worldgate.WorldGateMod;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Signs the local player in anonymously against Firebase Auth so every
 * request to the Realtime Database carries a UID the security rules can
 * check. Pure java.net.http -- no Minecraft classes, no Firebase SDK
 * dependency -- so this file is identical on every Minecraft version.
 */
public class FirebaseAuthClient {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final Pattern ID_TOKEN = Pattern.compile("\"idToken\"\\s*:\\s*\"(.*?)\"");
    private static final Pattern LOCAL_ID = Pattern.compile("\"localId\"\\s*:\\s*\"(.*?)\"");

    private String idToken;
    private String uid;

    public boolean signInAnonymously() {
        try {
            String url = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key="
                    + Constants.FIREBASE_API_KEY;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"returnSecureToken\":true}"))
                    .build();

            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            Matcher tokenMatcher = ID_TOKEN.matcher(body);
            Matcher uidMatcher = LOCAL_ID.matcher(body);

            if (tokenMatcher.find() && uidMatcher.find()) {
                this.idToken = tokenMatcher.group(1);
                this.uid = uidMatcher.group(1);
                WorldGateMod.LOGGER.info("WorldGate signed in anonymously as {}", uid);
                return true;
            }

            WorldGateMod.LOGGER.error("WorldGate Firebase sign-in failed: {}", body);
            return false;
        } catch (Exception e) {
            WorldGateMod.LOGGER.error("WorldGate Firebase sign-in error", e);
            return false;
        }
    }

    public String getIdToken() {
        return idToken;
    }

    public String getUid() {
        return uid;
    }
}
