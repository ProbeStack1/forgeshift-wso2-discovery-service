package com.forgeshift.wso2discovery.client;

import com.forgeshift.wso2discovery.config.Wso2Properties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Konnect wire format: the control-plane path, the Bearer header
 * and the adopt-on-conflict behaviour. These are the parts that fail silently
 * or with a misleading status when wrong.
 */
class KongAdminClientTest {

    private HttpServer server;
    private KongAdminClient client;
    private KonnectCredentials credentials;

    private final List<String> requestedPaths = new ArrayList<>();
    private final List<String> authHeaders = new ArrayList<>();
    private final List<String> requestBodies = new ArrayList<>();

    /** Response queued for the next request: status then body. */
    private int nextStatus = 201;
    private String nextBody = "{}";

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::handle);
        server.start();

        client = new KongAdminClient(WebClient.builder().build(), new Wso2Properties());
        credentials = KonnectCredentials.builder()
                .konnectBaseUrl("http://localhost:" + server.getAddress().getPort())
                .konnectAccessToken("kpat_test")
                .controlPlaneId("cp-1234")
                .build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void ensureConsumer_postsToControlPlaneScopedPathWithBearerAuth() {
        nextStatus = 201;
        nextBody = "{\"id\":\"consumer-uuid\",\"username\":\"api.developer\"}";

        KongAdminClient.ConsumerRef ref = client.ensureConsumer(credentials, "api.developer", "dev@example.com");

        assertEquals("consumer-uuid", ref.getId());
        assertEquals(KongAdminClient.WriteOutcome.CREATED, ref.getOutcome());
        // Konnect scopes every entity under the control plane; a flat /consumers 404s.
        assertEquals("/v2/control-planes/cp-1234/core-entities/consumers", requestedPaths.get(0));
        // Konnect rejects the self-managed Kong-Admin-Token header with a 401.
        assertEquals("Bearer kpat_test", authHeaders.get(0));
    }

    @Test
    void ensureConsumer_stampsMigrationTags() {
        nextStatus = 201;
        nextBody = "{\"id\":\"consumer-uuid\"}";

        client.ensureConsumer(credentials, "api.developer", null);

        String body = requestBodies.get(0);
        assertTrue(body.contains("migrated-by:forgeshift-wso2-migrator"), body);
        assertTrue(body.contains("wso2-source-user:api.developer"), body);
        // custom_id falls back to the username when no email is supplied.
        assertTrue(body.contains("\"custom_id\":\"api.developer\""), body);
    }

    @Test
    void ensureConsumer_adoptsExistingConsumerOnUniqueConstraint() {
        // First call: Kong rejects the duplicate. Second: the lookup succeeds.
        nextStatus = 409;
        nextBody = "{\"message\":\"UNIQUE violation detected on '{username=\\\"api.developer\\\"}' "
                + "(type: unique) constraint failed\"}";

        KongAdminClient.ConsumerRef ref = client.ensureConsumer(credentials, "api.developer", null);

        assertEquals(KongAdminClient.WriteOutcome.ALREADY_EXISTS, ref.getOutcome());
        // POST then GET /consumers/api.developer to adopt the existing one.
        assertEquals(2, requestedPaths.size());
        assertEquals("/v2/control-planes/cp-1234/core-entities/consumers/api.developer", requestedPaths.get(1));
    }

    @Test
    void ensureConsumer_propagatesRealFailures() {
        nextStatus = 401;
        nextBody = "{\"message\":\"Unauthorized\"}";

        WebClientResponseException ex = assertThrows(WebClientResponseException.class,
                () -> client.ensureConsumer(credentials, "api.developer", null));

        assertEquals(401, ex.getStatusCode().value());
        // A 401 must not be mistaken for an existing consumer.
        assertEquals(1, requestedPaths.size());
    }

    @Test
    void assignGroup_postsToConsumerAclPath() {
        nextStatus = 201;
        nextBody = "{\"group\":\"kong-subscriber\"}";

        KongAdminClient.WriteOutcome outcome =
                client.assignGroup(credentials, "consumer-uuid", "kong-subscriber");

        assertEquals(KongAdminClient.WriteOutcome.CREATED, outcome);
        assertEquals("/v2/control-planes/cp-1234/core-entities/consumers/consumer-uuid/acls",
                requestedPaths.get(0));
        assertTrue(requestBodies.get(0).contains("kong-subscriber"));
    }

    @Test
    void assignGroup_treatsDuplicateMembershipAsAlreadyExists() {
        nextStatus = 409;
        nextBody = "{\"message\":\"UNIQUE violation (type: unique) constraint failed\"}";

        KongAdminClient.WriteOutcome outcome =
                client.assignGroup(credentials, "consumer-uuid", "kong-subscriber");

        assertEquals(KongAdminClient.WriteOutcome.ALREADY_EXISTS, outcome);
    }

    @Test
    void encodesUsernamesThatAreNotUrlSafe() {
        nextStatus = 201;
        nextBody = "{\"group\":\"kong-subscriber\"}";

        client.assignGroup(credentials, "carbon.super/api dev", "kong-subscriber");

        // A raw slash would silently change which resource is addressed.
        assertTrue(requestedPaths.get(0).contains("carbon.super%2Fapi%20dev"), requestedPaths.get(0));
    }

    @Test
    void failsFastWhenControlPlaneIsMissing() {
        KonnectCredentials incomplete = KonnectCredentials.builder()
                .konnectBaseUrl("http://localhost:1")
                .konnectAccessToken("kpat_test")
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> client.ensureConsumer(incomplete, "api.developer", null));
        assertTrue(ex.getMessage().contains("control plane id"), ex.getMessage());
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestedPaths.add(exchange.getRequestURI().getRawPath());
        authHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
        requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        // The adopt path: the POST conflicts, the following GET resolves the id.
        int status = nextStatus;
        String body = nextBody;
        if (nextStatus == 409 && "GET".equals(exchange.getRequestMethod())) {
            status = 200;
            body = "{\"id\":\"consumer-uuid\",\"username\":\"api.developer\"}";
        }

        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }
}
