package com.mchekin.tipcurrent;

import com.mchekin.tipcurrent.TipcurrentApplication;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Optional;

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

    private String createUrl(String path) {
        return "http://localhost:" + port + path;
    }
}
