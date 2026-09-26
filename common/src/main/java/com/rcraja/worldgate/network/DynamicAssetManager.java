package com.rcraja.worldgate.network;

import com.rcraja.worldgate.Constants;
import com.rcraja.worldgate.WorldGateMod;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

public final class DynamicAssetManager {
    private static final long CACHE_LIMIT = 256L * 1024L * 1024L;
    private static final long MAX_ASSET_SIZE = 64L * 1024L * 1024L;
    private static final Path ROOT = Paths.get(System.getProperty("user.home"), ".worldgate", "assets");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(8))
            .build();

    private DynamicAssetManager() {}

    public static void refresh() {
        try {
            Files.createDirectories(ROOT);
            URI uri = URI.create(Constants.BACKEND_BASE_URL + "/v1/assets/manifest");
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) return;
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(java.time.Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String body = response.body();
                if (body != null && body.length() <= 2_000_000) {
                    try {
                        com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
                        if (root.has("version") && root.has("assets") && root.get("assets").isJsonArray()) {
                            Files.writeString(ROOT.resolve("manifest.json"), body);
                        }
                    } catch (Exception ignored) {
                        WorldGateMod.LOGGER.debug("WorldGate asset manifest JSON validation failed");
                    }
                }
                cleanup();
            }
        } catch (Exception e) {
            WorldGateMod.LOGGER.debug("WorldGate asset manifest refresh failed", e);
        }
    }

    public static Path download(String id, String url, String expectedSha256) {
        try {
            if (id == null || id.isBlank() || !expectedSha256.matches("[a-fA-F0-9]{64}")) return null;
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())) return null;
            Files.createDirectories(ROOT);

            Path target = ROOT.resolve(safe(id));
            if (Files.exists(target) && Files.size(target) <= MAX_ASSET_SIZE
                    && expectedSha256.equalsIgnoreCase(hash(target))) {
                return target;
            }

            Path tmp = ROOT.resolve(safe(id) + ".part");
            long existing = Files.exists(tmp) ? Files.size(tmp) : 0;
            if (existing > MAX_ASSET_SIZE) {
                Files.deleteIfExists(tmp);
                existing = 0;
            }

            HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                    .timeout(java.time.Duration.ofSeconds(30))
                    .GET();
            if (existing > 0) request.header("Range", "bytes=" + existing + "-");

            HttpResponse<InputStream> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) return null;

            long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            boolean append = existing > 0 && response.statusCode() == 206;
            if (declared >= 0 && existing + declared > MAX_ASSET_SIZE) return null;
            if (existing > 0 && response.statusCode() != 206) {
                existing = 0;
                Files.deleteIfExists(tmp);
                append = false;
            }

            try (InputStream in = response.body();
                 var out = Files.newOutputStream(tmp, append
                         ? new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND}
                         : new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING})) {
                byte[] buffer = new byte[8192];
                long written = existing;
                int n;
                while ((n = in.read(buffer)) != -1) {
                    written += n;
                    if (written > MAX_ASSET_SIZE) {
                        Files.deleteIfExists(tmp);
                        return null;
                    }
                    out.write(buffer, 0, n);
                }
            }

            if (!expectedSha256.equalsIgnoreCase(hash(tmp))) {
                Files.deleteIfExists(tmp);
                return null;
            }

            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            cleanup();
            return target;
        } catch (Exception e) {
            return null;
        }
    }

    private static String safe(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String hash(Path p) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(p)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) digest.update(buffer, 0, n);
        }
        StringBuilder result = new StringBuilder();
        for (byte b : digest.digest()) result.append(String.format("%02x", b));
        return result.toString();
    }

    private static void cleanup() throws Exception {
        if (!Files.exists(ROOT)) return;
        List<Path> files;
        try (var stream = Files.list(ROOT)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().equals("manifest.json"))
                    .toList();
        }
        long total = 0;
        for (Path p : files) total += Files.size(p);
        if (total <= CACHE_LIMIT) return;

        ArrayList<Path> sorted = new ArrayList<>(files);
        sorted.sort(Comparator.comparingLong(p -> {
            try { return Files.getLastModifiedTime(p).toMillis(); }
            catch (Exception e) { return 0; }
        }));
        for (Path p : sorted) {
            if (total <= CACHE_LIMIT) break;
            long size = Files.size(p);
            Files.deleteIfExists(p);
            total -= size;
        }
    }
}
