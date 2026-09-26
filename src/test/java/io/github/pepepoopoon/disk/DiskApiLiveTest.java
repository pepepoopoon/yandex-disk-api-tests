package io.github.pepepoopoon.disk;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in integration tests against a dedicated test account's real Disk. */
@EnabledIfEnvironmentVariable(named = "YANDEX_DISK_RUN_LIVE", matches = "true")
class DiskApiLiveTest {
    private static final Pattern DIRECTORY_TYPE = Pattern.compile("\"type\"\\s*:\\s*\"dir\"");
    private static final Pattern NON_EMPTY_ERROR = Pattern.compile("\"error\"\\s*:\\s*\"[^\"\\s][^\"]*\"");

    private DiskApiClient cleanupClient;
    private String cleanupRoot;

    @AfterEach
    void removeTestFolder() throws Exception {
        if (cleanupRoot == null) {
            return;
        }
        HttpResponse<String> cleanup = cleanupClient.deletePermanently(cleanupRoot);
        // A failed setup or a test may already have left the resource absent.
        assertTrue(cleanup.statusCode() == 204 || cleanup.statusCode() == 202 || cleanup.statusCode() == 404,
                "Cleanup returned " + cleanup.statusCode() + ": " + cleanup.body());
        awaitStatus(cleanupClient, cleanupRoot, 404);
    }

    @Test
    void createReadCopyAndDeleteFolders() throws Exception {
        DiskApiClient client = authenticatedClient();
        String root = createTestRoot(client);
        String source = root + "/source";
        String copy = root + "/copy";

        assertStatus(201, client.createFolder(source));
        assertDirectory(client.getResource(source));

        HttpResponse<String> copied = client.copyResource(source, copy);
        assertTrue(copied.statusCode() == 201 || copied.statusCode() == 202,
                "Copy returned " + copied.statusCode() + ": " + copied.body());
        awaitStatus(client, copy, 200);

        HttpResponse<String> deleted = client.deletePermanently(copy);
        assertTrue(deleted.statusCode() == 204 || deleted.statusCode() == 202,
                "Delete returned " + deleted.statusCode() + ": " + deleted.body());
        awaitStatus(client, copy, 404);
    }

    @Test
    void getWithInvalidTokenReturnsUnauthorized() throws Exception {
        DiskApiClient client = new DiskApiClient(DiskApiClient.PRODUCTION_API, "invalid-token-for-tests");

        assertApiError(401, client.getResource("disk:/"));
    }

    @Test
    void getMissingResourceReturnsNotFound() throws Exception {
        DiskApiClient client = authenticatedClient();

        assertApiError(404, client.getResource(uniquePath()));
    }

    @Test
    void deleteMissingResourceReturnsNotFound() throws Exception {
        DiskApiClient client = authenticatedClient();

        assertApiError(404, client.deletePermanently(uniquePath()));
    }

    @Test
    void duplicateFolderReturnsConflictAndKeepsOriginal() throws Exception {
        DiskApiClient client = authenticatedClient();
        String root = createTestRoot(client);

        assertApiError(409, client.createFolder(root));
        assertDirectory(client.getResource(root));
    }

    @Test
    void copyMissingSourceReturnsNotFoundAndCreatesNothing() throws Exception {
        DiskApiClient client = authenticatedClient();
        String root = createTestRoot(client);
        String destination = root + "/copy";

        assertApiError(404, client.copyResource(root + "/missing", destination));
        assertApiError(404, client.getResource(destination));
    }

    private static DiskApiClient authenticatedClient() {
        String token = System.getenv("YANDEX_DISK_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Set YANDEX_DISK_TOKEN for a dedicated test account");
        }
        return new DiskApiClient(DiskApiClient.PRODUCTION_API, token);
    }

    private String createTestRoot(DiskApiClient client) throws Exception {
        // Register ownership before the request so cleanup also runs after a timeout during setup.
        cleanupClient = client;
        cleanupRoot = uniquePath();
        assertStatus(201, client.createFolder(cleanupRoot));
        return cleanupRoot;
    }

    private static String uniquePath() {
        return "disk:/yandex-disk-api-tests-" + UUID.randomUUID();
    }

    private static void assertDirectory(HttpResponse<String> response) {
        assertStatus(200, response);
        assertTrue(DIRECTORY_TYPE.matcher(response.body()).find(), response.body());
    }

    private static void assertApiError(int expected, HttpResponse<String> response) {
        assertStatus(expected, response);
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/json"),
                "API errors must use JSON");
        assertTrue(NON_EMPTY_ERROR.matcher(response.body()).find(),
                "Missing non-empty error field: " + response.body());
    }

    private static void assertStatus(int expected, HttpResponse<String> response) {
        assertEquals(expected, response.statusCode(), response.body());
    }

    private static void awaitStatus(DiskApiClient client, String path, int expected) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        int actual;
        do {
            HttpResponse<String> response = client.getResource(path);
            actual = response.statusCode();
            if (actual == expected) {
                return;
            }
            assertTrue(actual == 200 || actual == 404,
                    "Unexpected HTTP " + actual + " while waiting for " + path + ": " + response.body());
            Thread.sleep(500);
        } while (Instant.now().isBefore(deadline));
        assertEquals(expected, actual, "Timed out waiting for " + path);
    }
}
