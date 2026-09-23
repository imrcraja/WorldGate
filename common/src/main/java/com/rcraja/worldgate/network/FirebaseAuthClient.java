package com.rcraja.worldgate.network;

import com.rcraja.worldgate.Constants;
import com.rcraja.worldgate.WorldGateMod;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps one anonymous Firebase identity across game launches by securely
 * persisting Firebase's refresh token locally and refreshing the ID token.
 * If the saved session is unavailable or revoked, a new anonymous account
 * is created as a fallback.
 */
public class FirebaseAuthClient {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final Preferences STORE =
            Preferences.userNodeForPackage(FirebaseAuthClient.class)
                    .node("worldgate-session");

    private static final Pattern ID_TOKEN =
            Pattern.compile("\"idToken\"\\s*:\\s*\"(.*?)\"");
    private static final Pattern LOCAL_ID =
            Pattern.compile("\"localId\"\\s*:\\s*\"(.*?)\"");
    private static final Pattern REFRESH_TOKEN =
            Pattern.compile("\"refreshToken\"\\s*:\\s*\"(.*?)\"");
    private static final Pattern EXPIRES_IN =
            Pattern.compile("\"expiresIn\"\\s*:\\s*\"(.*?)\"");

    private String idToken;
    private String uid;
    private String refreshToken;

    public synchronized boolean signInAnonymously() {
        if (restoreSavedSession()) {
            return true;
        }

        try {
            String url = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key="
                    + Constants.FIREBASE_API_KEY;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"returnSecureToken\":true}"))
                    .build();

            HttpResponse<String> response =
                    CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            Matcher tokenMatcher = ID_TOKEN.matcher(body);
            Matcher uidMatcher = LOCAL_ID.matcher(body);
            Matcher refreshMatcher = REFRESH_TOKEN.matcher(body);

            if (response.statusCode() >= 200
                    && response.statusCode() < 300
                    && tokenMatcher.find()
                    && uidMatcher.find()
                    && refreshMatcher.find()) {
                this.idToken = firstGroup(tokenMatcher);
                this.uid = uidMatcher.group(1);
                this.refreshToken = refreshMatcher.group(1);
                persistSession();
                WorldGateMod.LOGGER.info(
                        "WorldGate created a persistent anonymous Firebase identity {}",
                        uid
                );
                return true;
            }

            WorldGateMod.LOGGER.error(
                    "WorldGate Firebase sign-in failed (HTTP {}): {}",
                    response.statusCode(),
                    body
            );
            clearSavedSession();
            return false;
        } catch (Exception e) {
            WorldGateMod.LOGGER.error("WorldGate Firebase sign-in error", e);
            return false;
        }
    }

    private boolean restoreSavedSession() {
        String savedRefresh = STORE.get("refreshToken", null);
        String savedUid = STORE.get("uid", null);

        if (savedRefresh == null || savedRefresh.isBlank() || savedUid == null || savedUid.isBlank()) {
            return false;
        }

        try {
            String url = "https://securetoken.googleapis.com/v1/token?key="
                    + Constants.FIREBASE_API_KEY;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "grant_type=refresh_token&refresh_token=" + encode(savedRefresh)
                    ))
                    .build();

            HttpResponse<String> response =
                    CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            Matcher tokenMatcher = ID_TOKEN.matcher(body);
            Matcher refreshMatcher = REFRESH_TOKEN.matcher(body);
            Matcher uidMatcher = LOCAL_ID.matcher(body);

            if (response.statusCode() >= 200
                    && response.statusCode() < 300
                    && tokenMatcher.find()) {
                this.idToken = tokenMatcher.group(1);
                this.refreshToken = refreshMatcher.find()
                        ? refreshMatcher.group(1)
                        : savedRefresh;
                this.uid = uidMatcher.find() ? firstGroup(uidMatcher) : savedUid;
                persistSession();
                WorldGateMod.LOGGER.info(
                        "WorldGate restored Firebase identity {}",
                        uid
                );
                return true;
            }
        } catch (Exception e) {
            WorldGateMod.LOGGER.warn(
                    "WorldGate saved Firebase session could not be restored: {}",
                    e.getMessage()
            );
        }

        clearSavedSession();
        return false;
    }

    private static String firstGroup(Matcher matcher) {\n        for (int i = 1; i <= matcher.groupCount(); i++) {\n            String value = matcher.group(i);\n            if (value != null) return value;\n        }\n        return null;\n    }\n\n    private void persistSession() {
        if (uid == null || refreshToken == null) {
            return;
        }
        STORE.put("uid", uid);
        STORE.put("refreshToken", refreshToken);
        STORE.remove("idToken");
        STORE.remove("expiresIn");
    }

    private void clearSavedSession() {
        STORE.remove("uid");
        STORE.remove("refreshToken");
        STORE.remove("idToken");
        STORE.remove("expiresIn");
        idToken = null;
        uid = null;
        refreshToken = null;
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(
                value,
                java.nio.charset.StandardCharsets.UTF_8
        );
    }

    public String getIdToken() {
        return idToken;
    }

    public String getUid() {
        return uid;
    }
}
