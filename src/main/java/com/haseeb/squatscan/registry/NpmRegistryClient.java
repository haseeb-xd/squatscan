package com.haseeb.squatscan.registry;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
public class NpmRegistryClient {

    private static final String REGISTRY_BASE = "https://registry.npmjs.org/";
    private static final String DOWNLOADS_BASE = "https://api.npmjs.org/downloads/point/last-month/";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public NpmRegistryClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), new ObjectMapper());
    }

    NpmRegistryClient(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public NpmPackageData fetch(String packageName) {
        JsonNode root = fetchRegistryJson(packageName);
        if (root == null) {
            return NpmPackageData.notFound();
        }

        Instant firstPublished = parseFirstPublished(root);
        int versionCount = countVersions(root);
        boolean hasRepo = hasRepository(root);
        int maintainerCount = countMaintainers(root);
        long downloads = fetchLastMonthDownloads(packageName);

        return new NpmPackageData(true, firstPublished, versionCount, hasRepo, maintainerCount, downloads);
    }

    private JsonNode fetchRegistryJson(String packageName) {
        try {
            String encoded = encodePackageName(packageName);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(REGISTRY_BASE + encoded))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return null;
            }
            if (response.statusCode() != 200) {
                return null;
            }
            return objectMapper.readTree(response.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private long fetchLastMonthDownloads(String packageName) {
        try {
            String encoded = encodePackageName(packageName);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(DOWNLOADS_BASE + encoded))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return 0;
            }
            JsonNode node = objectMapper.readTree(response.body());
            JsonNode downloads = node.get("downloads");
            return downloads != null && downloads.isNumber() ? downloads.asLong() : 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return 0;
        }
    }

    private String encodePackageName(String packageName) {
        if (packageName.startsWith("@")) {
            int slash = packageName.indexOf('/');
            if (slash > 0) {
                String scope = packageName.substring(0, slash);
                String name = packageName.substring(slash + 1);
                return URLEncoder.encode(scope, StandardCharsets.UTF_8) + "/"
                        + URLEncoder.encode(name, StandardCharsets.UTF_8);
            }
        }
        return URLEncoder.encode(packageName, StandardCharsets.UTF_8);
    }

    private Instant parseFirstPublished(JsonNode root) {
        JsonNode time = root.get("time");
        if (time == null) {
            return null;
        }
        JsonNode created = time.get("created");
        if (created == null || !created.isString()) {
            return null;
        }
        try {
            return DateTimeFormatter.ISO_DATE_TIME.parse(created.asString(), Instant::from);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private int countVersions(JsonNode root) {
        JsonNode versions = root.get("versions");
        return versions != null && versions.isObject() ? versions.size() : 0;
    }

    private boolean hasRepository(JsonNode root) {
        JsonNode repository = root.get("repository");
        if (repository == null || repository.isNull()) {
            return false;
        }
        if (repository.isString()) {
            return !repository.asString().isBlank();
        }
        if (repository.isObject()) {
            JsonNode url = repository.get("url");
            return url != null && url.isString() && !url.asString().isBlank();
        }
        return false;
    }

    private int countMaintainers(JsonNode root) {
        JsonNode maintainers = root.get("maintainers");
        return maintainers != null && maintainers.isArray() ? maintainers.size() : 0;
    }
}
