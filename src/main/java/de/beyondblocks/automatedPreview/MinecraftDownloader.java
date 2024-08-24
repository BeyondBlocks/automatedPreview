package de.beyondblocks.automatedPreview;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class MinecraftDownloader {

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    private static Path saveToFile(InputStream inputStream, Path destinationPath) {
        try (OutputStream outputStream = Files.newOutputStream(destinationPath)) {
            inputStream.transferTo(outputStream);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file", e);
        }
        return destinationPath;
    }

    public static CompletableFuture<Path> downloadMinecraft(String version, Path destinationPath) {
        return getVersionManifestUrl(version)
                .thenCompose(MinecraftDownloader::getClientUrl)
                .thenCompose(clientUrl -> {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(clientUrl))
                            .GET()
                            .build();

                    return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                            .thenApply(response -> saveToFile(response.body(), destinationPath));
                });
    }

    private static CompletableFuture<String> getVersionManifestUrl(final String version) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://launchermeta.mojang.com/mc/game/version_manifest.json"))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(body -> {
                    JSONObject parsed = new JSONObject(body);
                    JSONArray versions = parsed.getJSONArray("versions");
                    for (int i = 0; i < versions.length(); i++) {
                        JSONObject versionData = versions.getJSONObject(i);
                        if (versionData.getString("id").equals(version)) {
                            return versionData.getString("url");
                        }
                    }
                    throw new RuntimeException("Version " + version + " not found");
                });
    }

    private static CompletableFuture<String> getClientUrl(final String versionManifestUrl) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(versionManifestUrl))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(body -> {
                    JSONObject parsed = new JSONObject(body);
                    return parsed.getJSONObject("downloads").getJSONObject("client").getString("url");
                });
    }
}
