package io.github.pepepoopoon.disk;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Small HTTP client for the four resource operations covered by this example. */
public final class DiskApiClient {
    public static final URI PRODUCTION_API = URI.create("https://cloud-api.yandex.net");

    private final HttpClient http;
    private final URI apiBase;
    private final String token;

    public DiskApiClient(URI apiBase, String token) {
        if (apiBase == null || !apiBase.isAbsolute()) {
            throw new IllegalArgumentException("API base must be an absolute URI");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("OAuth token must not be blank");
        }
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.apiBase = apiBase;
        this.token = token;
    }

    public HttpResponse<String> getResource(String path) throws IOException, InterruptedException {
        return send("GET", "/v1/disk/resources?path=" + encode(path));
    }

    public HttpResponse<String> createFolder(String path) throws IOException, InterruptedException {
        return send("PUT", "/v1/disk/resources?path=" + encode(path));
    }

    public HttpResponse<String> copyResource(String from, String path) throws IOException, InterruptedException {
        return send("POST", "/v1/disk/resources/copy?from=" + encode(from) + "&path=" + encode(path));
    }

    public HttpResponse<String> deletePermanently(String path) throws IOException, InterruptedException {
        return send("DELETE", "/v1/disk/resources?path=" + encode(path) + "&permanently=true");
    }

    private HttpResponse<String> send(String method, String resource) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(apiBase.resolve(resource))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "OAuth " + token)
                .header("Accept", "application/json")
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String encode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Disk path must not be blank");
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
