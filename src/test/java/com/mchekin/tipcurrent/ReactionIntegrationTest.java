package com.mchekin.tipcurrent;

import com.mchekin.tipcurrent.domain.Reaction;
import com.mchekin.tipcurrent.dto.CreateReactionRequest;
import com.mchekin.tipcurrent.dto.ReactionResponse;
import com.mchekin.tipcurrent.repository.ReactionRepository;
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
class ReactionIntegrationTest {

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
    private ReactionRepository reactionRepository;

    @BeforeEach
    void setUp() {
        reactionRepository.deleteAll();
    }

    @Test
    void shouldCreateReactionSuccessfully() {
        CreateReactionRequest request = CreateReactionRequest.builder()
                .roomId("gaming_stream_123")
                .userId("alice")
                .emoji("🔥")
                .targetId("msg_123")
                .metadata("{\"type\":\"fire\"}")
                .build();

        ResponseEntity<ReactionResponse> response = restTemplate.postForEntity(
                createUrl("/api/reactions"),
                request,
                ReactionResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();

        ReactionResponse reactionResponse = response.getBody();
        assertThat(reactionResponse.getId()).isNotNull();
        assertThat(reactionResponse.getRoomId()).isEqualTo("gaming_stream_123");
        assertThat(reactionResponse.getUserId()).isEqualTo("alice");
        assertThat(reactionResponse.getEmoji()).isEqualTo("🔥");
        assertThat(reactionResponse.getTargetId()).isEqualTo("msg_123");
        assertThat(reactionResponse.getMetadata()).isEqualTo("{\"type\":\"fire\"}");
        assertThat(reactionResponse.getCreatedAt()).isNotNull();

        Optional<Reaction> savedReaction = reactionRepository.findById(reactionResponse.getId());
        assertThat(savedReaction).isPresent();
        assertThat(savedReaction.get().getRoomId()).isEqualTo("gaming_stream_123");
        assertThat(savedReaction.get().getUserId()).isEqualTo("alice");
        assertThat(savedReaction.get().getEmoji()).isEqualTo("🔥");
    }

    @Test
    void shouldCreateReactionWithoutOptionalFields() {
        CreateReactionRequest request = CreateReactionRequest.builder()
                .roomId("webinar_room_456")
                .userId("user123")
                .emoji("❤️")
                .build();

        ResponseEntity<ReactionResponse> response = restTemplate.postForEntity(
                createUrl("/api/reactions"),
                request,
                ReactionResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();

        ReactionResponse reactionResponse = response.getBody();
        assertThat(reactionResponse.getId()).isNotNull();
        assertThat(reactionResponse.getTargetId()).isNull();
        assertThat(reactionResponse.getMetadata()).isNull();
        assertThat(reactionResponse.getEmoji()).isEqualTo("❤️");
    }

    @Test
    void shouldGetAllReactions() {
        createTestReaction("room1", "alice", "👍");
        createTestReaction("room1", "charlie", "🔥");
        createTestReaction("room2", "alice", "❤️");

        ResponseEntity<String> response = restTemplate.getForEntity(
                createUrl("/api/reactions"),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("alice");
        assertThat(response.getBody()).contains("charlie");
    }

    @Test
    void shouldGetReactionsByRoomId() {
        createTestReaction("room1", "alice", "👍");
        createTestReaction("room1", "charlie", "🔥");
        createTestReaction("room2", "alice", "❤️");

        ResponseEntity<String> response = restTemplate.getForEntity(
                createUrl("/api/reactions?roomId=room1"),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("room1");
        assertThat(response.getBody()).doesNotContain("room2");
    }

    @Test
    void shouldGetReactionsByUserId() {
        createTestReaction("room1", "alice", "👍");
        createTestReaction("room1", "charlie", "🔥");
        createTestReaction("room2", "alice", "❤️");

        ResponseEntity<String> response = restTemplate.getForEntity(
                createUrl("/api/reactions?userId=alice"),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"userId\":\"alice\"");
        assertThat(response.getBody()).doesNotContain("charlie");
    }

    @Test
    void shouldGetReactionsByEmoji() {
        createTestReaction("room1", "alice", "👍");
        createTestReaction("room1", "charlie", "🔥");
        createTestReaction("room1", "bob", "👍");

        ResponseEntity<String> response = restTemplate.getForEntity(
                createUrl("/api/reactions?roomId=room1&emoji=👍"),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("alice");
        assertThat(response.getBody()).contains("bob");
        assertThat(response.getBody()).doesNotContain("charlie");
    }

    @Test
    void shouldGetReactionById() {
        ReactionResponse created = createTestReaction("room1", "alice", "👍");

        ResponseEntity<ReactionResponse> response = restTemplate.getForEntity(
                createUrl("/api/reactions/" + created.getId()),
                ReactionResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(created.getId());
        assertThat(response.getBody().getRoomId()).isEqualTo("room1");
        assertThat(response.getBody().getUserId()).isEqualTo("alice");
    }

    @Test
    void shouldReturn404WhenReactionNotFound() {
        ResponseEntity<ReactionResponse> response = restTemplate.getForEntity(
                createUrl("/api/reactions/99999"),
                ReactionResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldSortReactionsByCreatedAtDesc() {
        ReactionResponse reaction1 = createTestReaction("room1", "alice", "👍");
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        ReactionResponse reaction2 = createTestReaction("room1", "charlie", "🔥");

        ResponseEntity<String> response = restTemplate.getForEntity(
                createUrl("/api/reactions?roomId=room1"),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        int reaction2Position = body.indexOf("\"id\":" + reaction2.getId());
        int reaction1Position = body.indexOf("\"id\":" + reaction1.getId());
        assertThat(reaction2Position).isLessThan(reaction1Position);
    }

    private ReactionResponse createTestReaction(String roomId, String userId, String emoji) {
        CreateReactionRequest request = CreateReactionRequest.builder()
                .roomId(roomId)
                .userId(userId)
                .emoji(emoji)
                .build();

        ResponseEntity<ReactionResponse> response = restTemplate.postForEntity(
                createUrl("/api/reactions"),
                request,
                ReactionResponse.class
        );

        return response.getBody();
    }

    @Test
    @SuppressWarnings("deprecation") // MappingJackson2MessageConverter deprecated but no replacement yet in Spring Boot 4.0.1
    void shouldBroadcastReactionViaWebSocket() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ReactionResponse> receivedMessage = new AtomicReference<>();

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
                return ReactionResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                receivedMessage.set((ReactionResponse) payload);
                latch.countDown();
            }
        });

        // Give subscription time to be fully registered
        Thread.sleep(2000);

        // Create a reaction via REST API
        CreateReactionRequest request = CreateReactionRequest.builder()
                .roomId("gaming_stream_123")
                .userId("alice")
                .emoji("🔥")
                .build();

        restTemplate.postForEntity(createUrl("/api/reactions"), request, ReactionResponse.class);

        // Wait for WebSocket message
        boolean received = latch.await(10, TimeUnit.SECONDS);

        assertThat(received).as("WebSocket message should be received within timeout").isTrue();
        ReactionResponse reactionResponse = receivedMessage.get();
        assertThat(reactionResponse).isNotNull();
        assertThat(reactionResponse.getRoomId()).isEqualTo("gaming_stream_123");
        assertThat(reactionResponse.getUserId()).isEqualTo("alice");
        assertThat(reactionResponse.getEmoji()).isEqualTo("🔥");

        session.disconnect();
    }

    private String createUrl(String path) {
        return "http://localhost:" + port + path;
    }
}
