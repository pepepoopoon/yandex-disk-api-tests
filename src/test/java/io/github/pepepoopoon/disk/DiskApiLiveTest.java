package io.github.pepepoopoon.disk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in smoke test against a dedicated test account's real Disk. */
@EnabledIfEnvironmentVariable(named = "YANDEX_DISK_RUN_LIVE", matches = "true")
class DiskApiLiveTest {
    private static final Pattern DIRECTORY_TYPE = Pattern.compile("\\\"type\\\"\\s*:\\s*\\\"dir\\\"");

    @Test
    void createReadCopyAndDeleteFolders() throws Exception {
        String token = System.getenv("YANDEX_DISK_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Set YANDEX_DISK_TOKEN for a dedicated test account");
        }

        DiskApiClient client = new DiskApiClient(DiskApiClient.PRODUCTION_API, token);
        String root = "disk:/yandex-disk-api-tests-" + UUID.randomUUID();
        String source = root + "/source";
        String copy = root + "/copy";
        boolean rootCreated = false;

        try {
            assertStatus(201, client.createFolder(root));
            rootCreated = true;
            assertStatus(201, client.createFolder(source));
            HttpResponse<String> metadata = client.getResource(source);
            assertStatus(200, metadata);
            assertTrue(DIRECTORY_TYPE.matcher(metadata.body()).find(), metadata.body());

            HttpResponse<String> copied = client.copyResource(source, copy);
            assertTrue(copied.statusCode() == 201 || copied.statusCode() == 202,
                    "Copy returned " + copied.statusCode() + ": " + copied.body());
            awaitStatus(client, copy, 200);

            HttpResponse<String> deleted = client.deletePermanently(copy);
            assertTrue(deleted.statusCode() == 204 || deleted.statusCode() == 202,
                    "Delete returned " + deleted.statusCode() + ": " + deleted.body());
            awaitStatus(client, copy, 404);
        } finally {
            if (rootCreated) {
                HttpResponse<String> cleanup = client.deletePermanently(root);
                assertTrue(cleanup.statusCode() == 204 || cleanup.statusCode() == 202,
                        "Cleanup returned " + cleanup.statusCode() + ": " + cleanup.body());
                awaitStatus(client, root, 404);
            }
        }
    }

    private static void assertStatus(int expected, HttpResponse<String> response) {
        assertEquals(expected, response.statusCode(), response.body());
    }

    private static void awaitStatus(DiskApiClient client, String path, int expected) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        int actual;
        do {
            actual = client.getResource(path).statusCode();
            if (actual == expected) {
                return;
            }
            Thread.sleep(500);
        } while (Instant.now().isBefore(deadline));
        assertEquals(expected, actual, "Timed out waiting for " + path);
    }
}
