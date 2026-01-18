package com.mchekin.tipcurrent;

import com.mchekin.tipcurrent.domain.IdempotencyRecord;
import com.mchekin.tipcurrent.dto.CreateTipRequest;
import com.mchekin.tipcurrent.dto.TipResponse;
import com.mchekin.tipcurrent.repository.IdempotencyRecordRepository;
import com.mchekin.tipcurrent.repository.TipRepository;
import com.mchekin.tipcurrent.scheduler.IdempotencyCleanupScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = TipcurrentApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureTestRestTemplate
@Testcontainers
class IdempotencyIntegrationTest {

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

    @Autowired
    private IdempotencyRecordRepository idempotencyRepository;

    @Autowired
    private IdempotencyCleanupScheduler cleanupScheduler;

    @BeforeEach
    void setUp() {
        tipRepository.deleteAll();
        idempotencyRepository.deleteAll();
    }

    @Test
    void shouldCreateTipWithIdempotencyKey() {
        String idempotencyKey = UUID.randomUUID().toString();
        CreateTipRequest request = CreateTipRequest.builder()
                .roomId("room1")
                .senderId("alice")
                .recipientId("bob")
                .amount(new BigDecimal("100.00"))
                .message("Test tip")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);

        HttpEntity<CreateTipRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<TipResponse> response = restTemplate.exchange(
                createUrl("/api/tips"),
                HttpMethod.POST,
                entity,
                TipResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isNotNull();

        // Verify idempotency record was created
        Optional<IdempotencyRecord> record = idempotencyRepository.findById(idempotencyKey);
        assertThat(record).isPresent();
        assertThat(record.get().getResourceId()).isEqualTo(response.getBody().getId());
        assertThat(record.get().getResourceType()).isEqualTo("Tip");
        assertThat(record.get().getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void shouldReturnSameTipOnRetryWithSameIdempotencyKey() {
        String idempotencyKey = UUID.randomUUID().toString();
        CreateTipRequest request = CreateTipRequest.builder()
                .roomId("room1")
                .senderId("alice")
                .recipientId("bob")
                .amount(new BigDecimal("100.00"))
                .message("Test tip")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);

        HttpEntity<CreateTipRequest> entity = new HttpEntity<>(request, headers);

        // First request
        ResponseEntity<TipResponse> response1 = restTemplate.exchange(
                createUrl("/api/tips"),
                HttpMethod.POST,
                entity,
                TipResponse.class
        );

        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long firstTipId = response1.getBody().getId();

        // Second request with same idempotency key (simulating retry)
        ResponseEntity<TipResponse> response2 = restTemplate.exchange(
                createUrl("/api/tips"),
                HttpMethod.POST,
                entity,
                TipResponse.class
        );

        // Should return same tip, not create a new one
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getBody().getId()).isEqualTo(firstTipId);

        // Verify only one tip was created
        assertThat(tipRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldCreateNewTipWithoutIdempotencyKey() {
        CreateTipRequest request = CreateTipRequest.builder()
                .roomId("room1")
                .senderId("alice")
                .recipientId("bob")
                .amount(new BigDecimal("100.00"))
                .build();

        // First request without idempotency key
        ResponseEntity<TipResponse> response1 = restTemplate.postForEntity(
                createUrl("/api/tips"),
                request,
                TipResponse.class
        );

        // Second request without idempotency key
        ResponseEntity<TipResponse> response2 = restTemplate.postForEntity(
                createUrl("/api/tips"),
                request,
                TipResponse.class
        );

        // Both should create new tips
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response1.getBody().getId()).isNotEqualTo(response2.getBody().getId());
        assertThat(tipRepository.count()).isEqualTo(2);
    }

    @Test
    void shouldCreateDifferentTipsWithDifferentIdempotencyKeys() {
        CreateTipRequest request = CreateTipRequest.builder()
                .roomId("room1")
                .senderId("alice")
                .recipientId("bob")
                .amount(new BigDecimal("100.00"))
                .build();

        HttpHeaders headers1 = new HttpHeaders();
        headers1.setContentType(MediaType.APPLICATION_JSON);
        headers1.set("Idempotency-Key", UUID.randomUUID().toString());

        HttpHeaders headers2 = new HttpHeaders();
        headers2.setContentType(MediaType.APPLICATION_JSON);
        headers2.set("Idempotency-Key", UUID.randomUUID().toString());

        ResponseEntity<TipResponse> response1 = restTemplate.exchange(
                createUrl("/api/tips"),
                HttpMethod.POST,
                new HttpEntity<>(request, headers1),
                TipResponse.class
        );

        ResponseEntity<TipResponse> response2 = restTemplate.exchange(
                createUrl("/api/tips"),
                HttpMethod.POST,
                new HttpEntity<>(request, headers2),
                TipResponse.class
        );

        // Different keys should create different tips
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response1.getBody().getId()).isNotEqualTo(response2.getBody().getId());
        assertThat(tipRepository.count()).isEqualTo(2);
        assertThat(idempotencyRepository.count()).isEqualTo(2);
    }

    @Test
    void shouldCleanupExpiredIdempotencyRecords() {
        // Create expired record directly in database
        IdempotencyRecord expiredRecord = IdempotencyRecord.builder()
                .idempotencyKey("expired-key-123")
                .resourceId(1L)
                .resourceType("Tip")
                .createdAt(Instant.now().minus(48, ChronoUnit.HOURS))
                .expiresAt(Instant.now().minus(24, ChronoUnit.HOURS))
                .build();
        idempotencyRepository.save(expiredRecord);

        // Create non-expired record
        IdempotencyRecord validRecord = IdempotencyRecord.builder()
                .idempotencyKey("valid-key-456")
                .resourceId(2L)
                .resourceType("Tip")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
        idempotencyRepository.save(validRecord);

        assertThat(idempotencyRepository.count()).isEqualTo(2);

        // Run cleanup
        cleanupScheduler.cleanupExpiredRecords();

        // Only valid record should remain
        assertThat(idempotencyRepository.count()).isEqualTo(1);
        assertThat(idempotencyRepository.findById("expired-key-123")).isEmpty();
        assertThat(idempotencyRepository.findById("valid-key-456")).isPresent();
    }

    @Test
    void shouldHandleIdempotencyKeyForConsecutiveRequests() {
        // Create 5 requests with same idempotency key
        String idempotencyKey = UUID.randomUUID().toString();
        CreateTipRequest request = CreateTipRequest.builder()
                .roomId("room1")
                .senderId("alice")
                .recipientId("bob")
                .amount(new BigDecimal("50.00"))
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);

        HttpEntity<CreateTipRequest> entity = new HttpEntity<>(request, headers);

        Long firstId = null;
        for (int i = 0; i < 5; i++) {
            ResponseEntity<TipResponse> response = restTemplate.exchange(
                    createUrl("/api/tips"),
                    HttpMethod.POST,
                    entity,
                    TipResponse.class
            );

            assertThat(response.getBody()).isNotNull();
            if (firstId == null) {
                firstId = response.getBody().getId();
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            } else {
                assertThat(response.getBody().getId()).isEqualTo(firstId);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            }
        }

        // Only one tip should exist
        assertThat(tipRepository.count()).isEqualTo(1);
        assertThat(idempotencyRepository.count()).isEqualTo(1);
    }

    private String createUrl(String path) {
        return "http://localhost:" + port + path;
    }
}
