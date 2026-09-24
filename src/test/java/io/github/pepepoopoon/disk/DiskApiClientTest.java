package io.github.pepepoopoon.disk;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiskApiClientTest {
    private HttpServer server;
    private DiskApiClient client;
    private final AtomicReference<ObservedRequest> lastRequest = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/disk/resources", this::respond);
        server.start();
        client = new DiskApiClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "test-token");
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void getRequestsResourceMetadata() throws Exception {
        var response = client.getResource("disk:/QA папка/файл.txt");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"type\":\"dir\""));
        assertRequest("GET", "/v1/disk/resources", "disk:/QA папка/файл.txt", null, null);
    }

    @Test
    void putCreatesFolder() throws Exception {
        var response = client.createFolder("disk:/QA папка");

        assertEquals(201, response.statusCode());
        assertRequest("PUT", "/v1/disk/resources", "disk:/QA папка", null, null);
    }

    @Test
    void postCopiesResource() throws Exception {
        var response = client.copyResource("disk:/QA папка/source", "disk:/QA папка/copy");

        assertEquals(201, response.statusCode());
        assertRequest("POST", "/v1/disk/resources/copy", "disk:/QA папка/copy", "disk:/QA папка/source", null);
    }

    @Test
    void deleteRemovesOnlyNamedResourcePermanently() throws Exception {
        var response = client.deletePermanently("disk:/QA папка/copy");

        assertEquals(204, response.statusCode());
        assertRequest("DELETE", "/v1/disk/resources", "disk:/QA папка/copy", null, "true");
    }

    private void assertRequest(String method, String endpoint, String path, String from, String permanently) {
        ObservedRequest actual = lastRequest.get();
        assertEquals(method, actual.method());
        assertEquals(endpoint, actual.endpoint());
        assertEquals("OAuth test-token", actual.authorization());
        assertEquals(path, actual.query().get("path"));
        assertEquals(from, actual.query().get("from"));
        assertEquals(permanently, actual.query().get("permanently"));
    }

    private void respond(HttpExchange exchange) throws IOException {
        try (exchange) {
            lastRequest.set(new ObservedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    parseQuery(exchange.getRequestURI().getRawQuery()),
                    exchange.getRequestHeaders().getFirst("Authorization")));
            int status = switch (exchange.getRequestMethod()) {
                case "GET" -> 200;
                case "POST", "PUT" -> 201;
                case "DELETE" -> 204;
                default -> 405;
            };
            byte[] body = status == 204 ? new byte[0] : "{\"type\":\"dir\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        return Arrays.stream(rawQuery.split("&"))
                .map(part -> part.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
    }

    private record ObservedRequest(String method, String endpoint, Map<String, String> query, String authorization) {}
}
