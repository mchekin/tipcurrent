package com.mchekin.tipcurrent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mchekin.tipcurrent.domain.Webhook;
import com.mchekin.tipcurrent.domain.WebhookDeliveryLog;
import com.mchekin.tipcurrent.dto.CreateTipRequest;
import com.mchekin.tipcurrent.dto.CreateWebhookRequest;
import com.mchekin.tipcurrent.dto.TipResponse;
import com.mchekin.tipcurrent.dto.WebhookResponse;
import com.mchekin.tipcurrent.repository.WebhookDeliveryLogRepository;
import com.mchekin.tipcurrent.repository.WebhookRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = TipcurrentApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureTestRestTemplate
@Testcontainers
class WebhookIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("tipcurrent_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private WebhookRepository webhookRepository;

    @Autowired
    private WebhookDeliveryLogRepository deliveryLogRepository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private HttpServer mockWebhookServer;
    private final int mockServerPort = 9999;
    private final List<ReceivedWebhook> receivedWebhooks = new CopyOnWriteArrayList<>();
    private CountDownLatch webhookLatch;

    @BeforeEach
    void setUp() throws IOException {
        webhookRepository.deleteAll();
        deliveryLogRepository.deleteAll();
        receivedWebhooks.clear();
        webhookLatch = new CountDownLatch(1);

        // Start mock webhook server
        mockWebhookServer = HttpServer.create(new InetSocketAddress(mockServerPort), 0);
        mockWebhookServer.createContext("/webhook", exchange -> {
            try {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String signature = exchange.getRequestHeaders().getFirst("X-TipCurrent-Signature");
                String event = exchange.getRequestHeaders().getFirst("X-TipCurrent-Event");

                receivedWebhooks.add(new ReceivedWebhook(body, signature, event));
                webhookLatch.countDown();

                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().close();
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, 0);
                exchange.getResponseBody().close();
            }
        });
        mockWebhookServer.start();
    }

    @AfterEach
    void tearDown() {
        if (mockWebhookServer != null) {
            mockWebhookServer.stop(0);
        }
    }

    @Test
    void shouldCreateWebhook() {
        CreateWebhookRequest request = new CreateWebhookRequest(
                "room1",
                "http://localhost:" + mockServerPort + "/webhook",
                "tip.created",
                "my-secret-key",
                "Test webhook"
        );

        ResponseEntity<WebhookResponse> response = restTemplate.postForEntity(
                createUrl("/api/webhooks"),
                request,
                WebhookResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getRoomId()).isEqualTo(request.getRoomId());
        assertThat(response.getBody().getUrl()).isEqualTo(request.getUrl());
        assertThat(response.getBody().getEvent()).isEqualTo(request.getEvent());
        assertThat(response.getBody().getEnabled()).isTrue();
        assertThat(response.getBody().getDescription()).isEqualTo(request.getDescription());
        assertThat(response.getBody().getCreatedAt()).isNotNull();
        assertThat(response.getBody().getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldListWebhooks() {
        // Create two webhooks for the same room
        Webhook webhook1 = createAndSaveWebhook("room1", "http://example.com/1", "tip.created", "secret1", "Webhook 1");
        Webhook webhook2 = createAndSaveWebhook("room1", "http://example.com/2", "tip.created", "secret2", "Webhook 2");

        ResponseEntity<WebhookResponse[]> response = restTemplate.getForEntity(
                createUrl("/api/webhooks"),
                WebhookResponse[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(2);

        List<Long> ids = List.of(response.getBody()[0].getId(), response.getBody()[1].getId());
        assertThat(ids).containsExactlyInAnyOrder(webhook1.getId(), webhook2.getId());
    }

    @Test
    void shouldGetWebhookById() {
        Webhook webhook = createAndSaveWebhook("room1", "http://example.com/webhook", "tip.created", "secret", "Test");

        ResponseEntity<WebhookResponse> response = restTemplate.getForEntity(
                createUrl("/api/webhooks/" + webhook.getId()),
                WebhookResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(webhook.getId());
        assertThat(response.getBody().getUrl()).isEqualTo(webhook.getUrl());
    }

    @Test
    void shouldReturn404WhenWebhookNotFound() {
        ResponseEntity<WebhookResponse> response = restTemplate.getForEntity(
                createUrl("/api/webhooks/99999"),
                WebhookResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldDeleteWebhook() {
        Webhook webhook = createAndSaveWebhook("room1", "http://example.com/webhook", "tip.created", "secret", "Test");

        restTemplate.delete(createUrl("/api/webhooks/" + webhook.getId()));

        assertThat(webhookRepository.findById(webhook.getId())).isEmpty();
    }

    @Test
    void shouldReturn404WhenDeletingNonexistentWebhook() {
        ResponseEntity<Void> response = restTemplate.exchange(
                createUrl("/api/webhooks/99999"),
                org.springframework.http.HttpMethod.DELETE,
                null,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldEnableWebhook() {
        Webhook webhook = createAndSaveWebhook("room1", "http://example.com/webhook", "tip.created", "secret", "Test");
        webhook.setEnabled(false);
        webhookRepository.save(webhook);

        restTemplate.patchForObject(
                createUrl("/api/webhooks/" + webhook.getId() + "/enable"),
                null,
                WebhookResponse.class
        );

        Webhook updated = webhookRepository.findById(webhook.getId()).orElseThrow();
        assertThat(updated.getEnabled()).isTrue();
    }

    @Test
    void shouldDisableWebhook() {
        Webhook webhook = createAndSaveWebhook("room1", "http://example.com/webhook", "tip.created", "secret", "Test");

        restTemplate.patchForObject(
                createUrl("/api/webhooks/" + webhook.getId() + "/disable"),
                null,
                WebhookResponse.class
        );

        Webhook updated = webhookRepository.findById(webhook.getId()).orElseThrow();
        assertThat(updated.getEnabled()).isFalse();
    }

    @Test
    void shouldDeliverWebhookWhenTipCreated() throws Exception {
        String secret = "test-secret-key";
        createAndSaveWebhook("room1", "http://localhost:" + mockServerPort + "/webhook", "tip.created", secret, "Test");

        CreateTipRequest tipRequest = new CreateTipRequest(
                "room1",
                "alice",
                "bob",
                new BigDecimal("100.00"),
                "Great stream!",
                null
        );

        ResponseEntity<TipResponse> tipResponse = restTemplate.postForEntity(
                createUrl("/api/tips"),
                tipRequest,
                TipResponse.class
        );

        assertThat(tipResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Wait for webhook delivery (async)
        boolean received = webhookLatch.await(10, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(receivedWebhooks).hasSize(1);

        ReceivedWebhook webhook = receivedWebhooks.getFirst();
        assertThat(webhook.event()).isEqualTo("tip.created");

        // Verify payload contains tip data
        TipResponse deliveredTip = objectMapper.readValue(webhook.body(), TipResponse.class);
        assertThat(deliveredTip.getRoomId()).isEqualTo("room1");
        assertThat(deliveredTip.getSenderId()).isEqualTo("alice");
        assertThat(deliveredTip.getRecipientId()).isEqualTo("bob");
        assertThat(deliveredTip.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));

        // Verify HMAC signature
        String expectedSignature = calculateHMAC(webhook.body(), secret);
        assertThat(webhook.signature()).isEqualTo(expectedSignature);
    }

    @Test
    void shouldNotDeliverWebhookWhenDisabled() throws Exception {
        Webhook webhook = createAndSaveWebhook(
                "room1",
                "http://localhost:" + mockServerPort + "/webhook",
                "tip.created",
                "secret",
                "Test"
        );
        webhook.setEnabled(false);
        webhookRepository.save(webhook);

        CreateTipRequest tipRequest = new CreateTipRequest(
                "room1",
                "alice",
                "bob",
                new BigDecimal("100.00"),
                "Great stream!",
                null
        );

        restTemplate.postForEntity(createUrl("/api/tips"), tipRequest, TipResponse.class);

        // Wait a bit to ensure webhook would have been delivered if enabled
        boolean received = webhookLatch.await(2, TimeUnit.SECONDS);
        assertThat(received).isFalse();
        assertThat(receivedWebhooks).isEmpty();
    }

    @Test
    void shouldLogSuccessfulWebhookDelivery() throws Exception {
        Webhook webhook = createAndSaveWebhook(
                "room1",
                "http://localhost:" + mockServerPort + "/webhook",
                "tip.created",
                "secret",
                "Test"
        );

        CreateTipRequest tipRequest = new CreateTipRequest(
                "room1",
                "alice",
                "bob",
                new BigDecimal("100.00"),
                "Great stream!",
                null
        );

        restTemplate.postForEntity(createUrl("/api/tips"), tipRequest, TipResponse.class);

        // Wait for webhook delivery
        boolean received = webhookLatch.await(10, TimeUnit.SECONDS);
        assertThat(received).as("Webhook should be delivered within timeout").isTrue();

        // Wait a bit more for logging to complete
        Thread.sleep(1000);

        List<WebhookDeliveryLog> logs = deliveryLogRepository.findAll();
        assertThat(logs).hasSize(1);

        WebhookDeliveryLog log = logs.getFirst();
        assertThat(log.getWebhookId()).isEqualTo(webhook.getId());
        assertThat(log.getEvent()).isEqualTo("tip.created");
        assertThat(log.getSuccess()).isTrue();
        assertThat(log.getHttpStatusCode()).isEqualTo(200);
        assertThat(log.getAttemptNumber()).isEqualTo(1);
        assertThat(log.getDurationMs()).isGreaterThan(0L);
    }

    @Test
    void shouldLogFailedWebhookDelivery() throws Exception {
        // Create webhook pointing to non-existent server
        Webhook webhook = createAndSaveWebhook(
                "room1",
                "http://localhost:9998/webhook",  // Wrong port - nothing listening
                "tip.created",
                "secret",
                "Test"
        );

        CreateTipRequest tipRequest = new CreateTipRequest(
                "room1",
                "alice",
                "bob",
                new BigDecimal("100.00"),
                "Great stream!",
                null
        );

        restTemplate.postForEntity(createUrl("/api/tips"), tipRequest, TipResponse.class);

        // Wait for webhook delivery attempts (initial + 1 retry after 5s delay)
        // Use longer timeout for CI environments which can be slower
        Thread.sleep(12000);

        List<WebhookDeliveryLog> logs = deliveryLogRepository.findAll();
        assertThat(logs).hasSizeGreaterThanOrEqualTo(1);  // At least initial attempt

        // Check that all attempts failed
        for (WebhookDeliveryLog log : logs) {
            assertThat(log.getWebhookId()).isEqualTo(webhook.getId());
            assertThat(log.getEvent()).isEqualTo("tip.created");
            assertThat(log.getSuccess()).isFalse();
        }
    }

    @Test
    void shouldGetWebhookDeliveryLogs() throws Exception {
        Webhook webhook = createAndSaveWebhook(
                "room1",
                "http://localhost:" + mockServerPort + "/webhook",
                "tip.created",
                "secret",
                "Test"
        );

        // Create a tip to trigger webhook
        CreateTipRequest tipRequest = new CreateTipRequest(
                "room1",
                "alice",
                "bob",
                new BigDecimal("100.00"),
                "Great stream!",
                null
        );

        restTemplate.postForEntity(createUrl("/api/tips"), tipRequest, TipResponse.class);

        // Wait for delivery
        boolean received = webhookLatch.await(10, TimeUnit.SECONDS);
        assertThat(received).as("Webhook should be delivered within timeout").isTrue();
        Thread.sleep(1000);  // Wait for logging

        ResponseEntity<String> response = restTemplate.getForEntity(
                createUrl("/api/webhooks/" + webhook.getId() + "/deliveries?page=0&size=20"),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"success\":true");
        assertThat(response.getBody()).contains("\"event\":\"tip.created\"");
    }

    @Test
    void shouldTestWebhook() throws Exception {
        Webhook webhook = createAndSaveWebhook(
                "room1",
                "http://localhost:" + mockServerPort + "/webhook",
                "tip.created",
                "secret",
                "Test"
        );

        ResponseEntity<Void> response = restTemplate.postForEntity(
                createUrl("/api/webhooks/" + webhook.getId() + "/test"),
                null,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Wait for test webhook delivery
        boolean received = webhookLatch.await(10, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(receivedWebhooks).hasSize(1);

        ReceivedWebhook receivedWebhook = receivedWebhooks.getFirst();
        assertThat(receivedWebhook.event()).isEqualTo("test.event");
        assertThat(receivedWebhook.body()).contains("This is a test webhook delivery");
    }

    @Test
    void shouldReturn404WhenTestingNonexistentWebhook() {
        ResponseEntity<Void> response = restTemplate.postForEntity(
                createUrl("/api/webhooks/99999/test"),
                null,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldDeliverMultipleWebhooksForSameEvent() throws Exception {
        String secret1 = "secret1";
        String secret2 = "secret2";

        // Both webhooks for the same room
        createAndSaveWebhook("room1", "http://localhost:" + mockServerPort + "/webhook", "tip.created", secret1, "Webhook 1");
        createAndSaveWebhook("room1", "http://localhost:" + mockServerPort + "/webhook", "tip.created", secret2, "Webhook 2");

        webhookLatch = new CountDownLatch(2);  // Expect 2 deliveries

        CreateTipRequest tipRequest = new CreateTipRequest(
                "room1",
                "alice",
                "bob",
                new BigDecimal("100.00"),
                "Great stream!",
                null
        );

        restTemplate.postForEntity(createUrl("/api/tips"), tipRequest, TipResponse.class);

        // Wait for both webhooks
        boolean received = webhookLatch.await(10, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(receivedWebhooks).hasSize(2);

        // Verify both received the same tip data but different signatures
        assertThat(receivedWebhooks.get(0).body()).isEqualTo(receivedWebhooks.get(1).body());
        assertThat(receivedWebhooks.get(0).signature()).isNotEqualTo(receivedWebhooks.get(1).signature());
    }

    @Test
    void shouldNotDeliverWebhookForDifferentRoom() throws Exception {
        // Webhook is registered for room2, but tip is created in room1
        createAndSaveWebhook("room2", "http://localhost:" + mockServerPort + "/webhook", "tip.created", "secret", "Test");

        CreateTipRequest tipRequest = new CreateTipRequest(
                "room1",  // Different room!
                "alice",
                "bob",
                new BigDecimal("100.00"),
                "Great stream!",
                null
        );

        restTemplate.postForEntity(createUrl("/api/tips"), tipRequest, TipResponse.class);

        // Webhook should NOT be delivered because it's for a different room
        boolean received = webhookLatch.await(2, TimeUnit.SECONDS);
        assertThat(received).isFalse();
        assertThat(receivedWebhooks).isEmpty();
    }

    @Test
    void shouldCreateGlobalWebhook() {
        // Create webhook without roomId (global)
        CreateWebhookRequest request = new CreateWebhookRequest(
                null,  // null roomId = global webhook
                "http://localhost:" + mockServerPort + "/webhook",
                "tip.created",
                "my-secret-key",
                "Global webhook for all rooms"
        );

        ResponseEntity<WebhookResponse> response = restTemplate.postForEntity(
                createUrl("/api/webhooks"),
                request,
                WebhookResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getRoomId()).isNull();
        assertThat(response.getBody().getDescription()).isEqualTo("Global webhook for all rooms");
    }

    @Test
    void shouldDeliverGlobalWebhookForAnyRoom() throws Exception {
        // Create a global webhook (no roomId)
        createAndSaveGlobalWebhook("http://localhost:" + mockServerPort + "/webhook", "tip.created", "secret", "Global");

        // Create tip in room1 - global webhook should fire
        CreateTipRequest tipRequest = new CreateTipRequest(
                "room1",
                "alice",
                "bob",
                new BigDecimal("100.00"),
                "Great stream!",
                null
        );

        restTemplate.postForEntity(createUrl("/api/tips"), tipRequest, TipResponse.class);

        boolean received = webhookLatch.await(10, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(receivedWebhooks).hasSize(1);

        // Create tip in room2 - same global webhook should fire again
        receivedWebhooks.clear();
        webhookLatch = new CountDownLatch(1);

        CreateTipRequest tipRequest2 = new CreateTipRequest(
                "room2",  // Different room
                "charlie",
                "dave",
                new BigDecimal("50.00"),
                "Nice!",
                null
        );

        restTemplate.postForEntity(createUrl("/api/tips"), tipRequest2, TipResponse.class);

        received = webhookLatch.await(10, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(receivedWebhooks).hasSize(1);
    }

    @Test
    void shouldDeliverBothGlobalAndRoomWebhooks() throws Exception {
        // Create a global webhook
        createAndSaveGlobalWebhook("http://localhost:" + mockServerPort + "/webhook", "tip.created", "global-secret", "Global");
        // Create a room-specific webhook for room1
        createAndSaveWebhook("room1", "http://localhost:" + mockServerPort + "/webhook", "tip.created", "room-secret", "Room1");

        webhookLatch = new CountDownLatch(2);  // Expect 2 deliveries

        CreateTipRequest tipRequest = new CreateTipRequest(
                "room1",
                "alice",
                "bob",
                new BigDecimal("100.00"),
                "Great stream!",
                null
        );

        restTemplate.postForEntity(createUrl("/api/tips"), tipRequest, TipResponse.class);

        boolean received = webhookLatch.await(10, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(receivedWebhooks).hasSize(2);

        // Both webhooks fired - different signatures due to different secrets
        assertThat(receivedWebhooks.get(0).signature()).isNotEqualTo(receivedWebhooks.get(1).signature());
    }

    @Test
    void shouldListOnlyGlobalWebhooks() {
        // Create a global webhook
        createAndSaveGlobalWebhook("http://example.com/global", "tip.created", "secret", "Global webhook");
        // Create room-specific webhooks
        createAndSaveWebhook("room1", "http://example.com/room1", "tip.created", "secret", "Room1 webhook");
        createAndSaveWebhook("room2", "http://example.com/room2", "tip.created", "secret", "Room2 webhook");

        // List only global webhooks
        ResponseEntity<WebhookResponse[]> response = restTemplate.getForEntity(
                createUrl("/api/webhooks?global=true"),
                WebhookResponse[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].getRoomId()).isNull();
        assertThat(response.getBody()[0].getDescription()).isEqualTo("Global webhook");
    }

    @Test
    void shouldListWebhooksForSpecificRoom() {
        // Create webhooks for different rooms and a global one
        createAndSaveGlobalWebhook("http://example.com/global", "tip.created", "secret", "Global");
        createAndSaveWebhook("room1", "http://example.com/room1-a", "tip.created", "secret", "Room1-A");
        createAndSaveWebhook("room1", "http://example.com/room1-b", "tip.created", "secret", "Room1-B");
        createAndSaveWebhook("room2", "http://example.com/room2", "tip.created", "secret", "Room2");

        // List only room1 webhooks
        ResponseEntity<WebhookResponse[]> response = restTemplate.getForEntity(
                createUrl("/api/webhooks?roomId=room1"),
                WebhookResponse[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(2);
        for (WebhookResponse webhook : response.getBody()) {
            assertThat(webhook.getRoomId()).isEqualTo("room1");
        }
    }

    private void createAndSaveGlobalWebhook(String url, String event, String secret, String description) {
        Webhook webhook = Webhook.builder()
                .roomId(null)  // Global webhook
                .url(url)
                .event(event)
                .secret(secret)
                .description(description)
                .enabled(true)
                .build();
        webhookRepository.save(webhook);
    }

    private Webhook createAndSaveWebhook(String roomId, String url, String event, String secret, String description) {
        Webhook webhook = Webhook.builder()
                .roomId(roomId)
                .url(url)
                .event(event)
                .secret(secret)
                .description(description)
                .enabled(true)
                .build();
        return webhookRepository.save(webhook);
    }

    private String createUrl(String path) {
        return "http://localhost:" + port + path;
    }

    private String calculateHMAC(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC", e);
        }
    }

    private record ReceivedWebhook(String body, String signature, String event) {}
}
