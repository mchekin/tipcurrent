package com.mchekin.tipcurrent;

import com.mchekin.tipcurrent.domain.Tip;
import com.mchekin.tipcurrent.dto.CreateTipRequest;
import com.mchekin.tipcurrent.dto.TipResponse;
import com.mchekin.tipcurrent.repository.TipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = TipcurrentApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureTestRestTemplate
@Testcontainers
class TipIntegrationTest {

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
    private TipRepository tipRepository;

    @BeforeEach
    void setUp() {
        tipRepository.deleteAll();
    }

    @Test
    void shouldCreateTipSuccessfully() {
        CreateTipRequest request = CreateTipRequest.builder()
                .roomId("gaming_stream_123")
                .senderId("alice")
                .recipientId("bob")
                .amount(new BigDecimal("100.00"))
                .message("Great play!")
                .metadata("{\"type\":\"celebration\"}")
                .build();

        ResponseEntity<TipResponse> response = restTemplate.postForEntity(
                createUrl("/api/tips"),
                request,
                TipResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();

        TipResponse tipResponse = response.getBody();
        assertThat(tipResponse.getId()).isNotNull();
        assertThat(tipResponse.getRoomId()).isEqualTo("gaming_stream_123");
        assertThat(tipResponse.getSenderId()).isEqualTo("alice");
        assertThat(tipResponse.getRecipientId()).isEqualTo("bob");
        assertThat(tipResponse.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(tipResponse.getMessage()).isEqualTo("Great play!");
        assertThat(tipResponse.getMetadata()).isEqualTo("{\"type\":\"celebration\"}");
        assertThat(tipResponse.getCreatedAt()).isNotNull();

        Optional<Tip> savedTip = tipRepository.findById(tipResponse.getId());
        assertThat(savedTip).isPresent();
        assertThat(savedTip.get().getRoomId()).isEqualTo("gaming_stream_123");
        assertThat(savedTip.get().getSenderId()).isEqualTo("alice");
        assertThat(savedTip.get().getRecipientId()).isEqualTo("bob");
    }

    @Test
    void shouldCreateTipWithoutOptionalFields() {
        CreateTipRequest request = CreateTipRequest.builder()
                .roomId("webinar_room_456")
                .senderId("user123")
                .recipientId("host456")
                .amount(new BigDecimal("50.00"))
                .build();

        ResponseEntity<TipResponse> response = restTemplate.postForEntity(
                createUrl("/api/tips"),
                request,
                TipResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();

        TipResponse tipResponse = response.getBody();
        assertThat(tipResponse.getId()).isNotNull();
        assertThat(tipResponse.getMessage()).isNull();
        assertThat(tipResponse.getMetadata()).isNull();
        assertThat(tipResponse.getAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void shouldGetAllTips() {
        createTestTip("room1", "alice", "bob", new BigDecimal("100"));
        createTestTip("room1", "charlie", "bob", new BigDecimal("200"));
        createTestTip("room2", "alice", "david", new BigDecimal("50"));

        ResponseEntity<String> response = restTemplate.getForEntity(
                createUrl("/api/tips"),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("alice");
        assertThat(response.getBody()).contains("bob");
        assertThat(response.getBody()).contains("charlie");
    }

    @Test
    void shouldGetTipsByRoomId() {
        createTestTip("room1", "alice", "bob", new BigDecimal("100"));
        createTestTip("room1", "charlie", "bob", new BigDecimal("200"));
        createTestTip("room2", "alice", "david", new BigDecimal("50"));

        ResponseEntity<String> response = restTemplate.getForEntity(
                createUrl("/api/tips?roomId=room1"),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("room1");
        assertThat(response.getBody()).doesNotContain("room2");
    }

    @Test
    void shouldGetTipsByRecipientId() {
        createTestTip("room1", "alice", "bob", new BigDecimal("100"));
        createTestTip("room1", "charlie", "bob", new BigDecimal("200"));
        createTestTip("room2", "alice", "david", new BigDecimal("50"));

        ResponseEntity<String> response = restTemplate.getForEntity(
                createUrl("/api/tips?recipientId=bob"),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"recipientId\":\"bob\"");
        assertThat(response.getBody()).doesNotContain("david");
    }

    @Test
    void shouldGetTipsBySenderId() {
        createTestTip("room1", "alice", "bob", new BigDecimal("100"));
        createTestTip("room1", "charlie", "bob", new BigDecimal("200"));
        createTestTip("room2", "alice", "david", new BigDecimal("50"));

        ResponseEntity<String> response = restTemplate.getForEntity(
                createUrl("/api/tips?senderId=alice"),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"senderId\":\"alice\"");
        assertThat(response.getBody()).doesNotContain("charlie");
    }

    @Test
    void shouldGetTipById() {
        TipResponse created = createTestTip("room1", "alice", "bob", new BigDecimal("100"));

        ResponseEntity<TipResponse> response = restTemplate.getForEntity(
                createUrl("/api/tips/" + created.getId()),
                TipResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(created.getId());
        assertThat(response.getBody().getRoomId()).isEqualTo("room1");
        assertThat(response.getBody().getSenderId()).isEqualTo("alice");
    }

    @Test
    void shouldReturn404WhenTipNotFound() {
        ResponseEntity<TipResponse> response = restTemplate.getForEntity(
                createUrl("/api/tips/99999"),
                TipResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldSortTipsByCreatedAtDesc() {
        TipResponse tip1 = createTestTip("room1", "alice", "bob", new BigDecimal("100"));
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        TipResponse tip2 = createTestTip("room1", "charlie", "bob", new BigDecimal("200"));

        ResponseEntity<String> response = restTemplate.getForEntity(
                createUrl("/api/tips?roomId=room1"),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        int tip2Position = body.indexOf("\"id\":" + tip2.getId());
        int tip1Position = body.indexOf("\"id\":" + tip1.getId());
        assertThat(tip2Position).isLessThan(tip1Position);
    }

    private TipResponse createTestTip(String roomId, String senderId, String recipientId, BigDecimal amount) {
        CreateTipRequest request = CreateTipRequest.builder()
                .roomId(roomId)
                .senderId(senderId)
                .recipientId(recipientId)
                .amount(amount)
                .build();

        ResponseEntity<TipResponse> response = restTemplate.postForEntity(
                createUrl("/api/tips"),
                request,
                TipResponse.class
        );

        return response.getBody();
    }

    @Test
    @SuppressWarnings("deprecation") // MappingJackson2MessageConverter deprecated but no replacement yet in Spring Boot 4.0.1
    void shouldBroadcastTipViaWebSocket() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<TipResponse> receivedMessage = new AtomicReference<>();

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        MappingJackson2MessageConverter messageConverter = new MappingJackson2MessageConverter();
        messageConverter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(messageConverter);

        StompSession session = stompClient
                .connectAsync(String.format("ws://localhost:%d/ws", port), new StompSessionHandlerAdapter() {})
                .get(10, TimeUnit.SECONDS);

        session.subscribe("/topic/rooms/gaming_stream_123", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TipResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                receivedMessage.set((TipResponse) payload);
                latch.countDown();
            }
        });

        // Give subscription time to be fully registered
        Thread.sleep(2000);

        // Create a tip via REST API
        CreateTipRequest request = CreateTipRequest.builder()
                .roomId("gaming_stream_123")
                .senderId("alice")
                .recipientId("bob")
                .amount(new BigDecimal("100.00"))
                .message("WebSocket test")
                .build();

        restTemplate.postForEntity(createUrl("/api/tips"), request, TipResponse.class);

        // Wait for WebSocket message
        boolean received = latch.await(10, TimeUnit.SECONDS);

        assertThat(received).as("WebSocket message should be received within timeout").isTrue();
        TipResponse tipResponse = receivedMessage.get();
        assertThat(tipResponse).isNotNull();
        assertThat(tipResponse.getRoomId()).isEqualTo("gaming_stream_123");
        assertThat(tipResponse.getSenderId()).isEqualTo("alice");
        assertThat(tipResponse.getRecipientId()).isEqualTo("bob");
        assertThat(tipResponse.getMessage()).isEqualTo("WebSocket test");

        session.disconnect();
    }

    private String createUrl(String path) {
        return "http://localhost:" + port + path;
    }
}
