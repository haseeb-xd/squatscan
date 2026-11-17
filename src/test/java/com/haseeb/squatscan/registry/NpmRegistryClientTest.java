package com.haseeb.squatscan.registry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class NpmRegistryClientTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> registryResponse;

    @Mock
    private HttpResponse<String> downloadsResponse;

    private NpmRegistryClient client;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        client = new NpmRegistryClient(httpClient, new ObjectMapper());
    }

    @Test
    void returnsNotFoundOn404() throws IOException, InterruptedException {
        when(registryResponse.statusCode()).thenReturn(404);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(registryResponse);

        NpmPackageData data = client.fetch("some-hallucinated-package-xyz");

        assertThat(data.exists()).isFalse();
        assertThat(data.versionCount()).isEqualTo(0);
        assertThat(data.lastMonthDownloads()).isEqualTo(0);
    }

    @Test
    void parsesExistingPackageWithFullMetadata() throws IOException, InterruptedException {
        String registryJson = """
                {
                  "name": "express",
                  "time": { "created": "2010-12-29T19:38:25.137Z" },
                  "versions": { "4.0.0": {}, "4.1.0": {}, "4.2.0": {} },
                  "repository": { "type": "git", "url": "git+https://github.com/expressjs/express.git" },
                  "maintainers": [ { "name": "a" }, { "name": "b" } ]
                }
                """;
        String downloadsJson = """
                { "downloads": 50000000, "start": "2024-01-01", "end": "2024-01-31", "package": "express" }
                """;

        when(registryResponse.statusCode()).thenReturn(200);
        when(registryResponse.body()).thenReturn(registryJson);
        when(downloadsResponse.statusCode()).thenReturn(200);
        when(downloadsResponse.body()).thenReturn(downloadsJson);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(registryResponse)
                .thenReturn(downloadsResponse);

        NpmPackageData data = client.fetch("express");

        assertThat(data.exists()).isTrue();
        assertThat(data.versionCount()).isEqualTo(3);
        assertThat(data.hasRepo()).isTrue();
        assertThat(data.maintainerCount()).isEqualTo(2);
        assertThat(data.lastMonthDownloads()).isEqualTo(50000000);
        assertThat(data.firstPublished()).isNotNull();
    }

    @Test
    void handlesMissingFieldsDefensively() throws IOException, InterruptedException {
        String registryJson = """
                { "name": "bare-package" }
                """;
        String downloadsJson = """
                { "error": "package not found" }
                """;

        when(registryResponse.statusCode()).thenReturn(200);
        when(registryResponse.body()).thenReturn(registryJson);
        when(downloadsResponse.statusCode()).thenReturn(404);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(registryResponse)
                .thenReturn(downloadsResponse);

        NpmPackageData data = client.fetch("bare-package");

        assertThat(data.exists()).isTrue();
        assertThat(data.firstPublished()).isNull();
        assertThat(data.versionCount()).isEqualTo(0);
        assertThat(data.hasRepo()).isFalse();
        assertThat(data.maintainerCount()).isEqualTo(0);
        assertThat(data.lastMonthDownloads()).isEqualTo(0);
    }

    @Test
    void handlesStringRepositoryField() throws IOException, InterruptedException {
        String registryJson = """
                { "name": "old-style", "repository": "github:user/repo" }
                """;
        when(registryResponse.statusCode()).thenReturn(200);
        when(registryResponse.body()).thenReturn(registryJson);
        when(downloadsResponse.statusCode()).thenReturn(404);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(registryResponse)
                .thenReturn(downloadsResponse);

        NpmPackageData data = client.fetch("old-style");

        assertThat(data.hasRepo()).isTrue();
    }
}
