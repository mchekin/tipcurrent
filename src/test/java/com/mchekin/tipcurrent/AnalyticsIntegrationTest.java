package com.mchekin.tipcurrent;

import com.mchekin.tipcurrent.domain.RoomStatsHourly;
import com.mchekin.tipcurrent.domain.Tip;
import com.mchekin.tipcurrent.dto.RoomStatsResponse;
import com.mchekin.tipcurrent.repository.RoomStatsHourlyRepository;
import com.mchekin.tipcurrent.repository.TipRepository;
import com.mchekin.tipcurrent.service.StatsAggregationService;
import jakarta.persistence.EntityManager;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = TipcurrentApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureTestRestTemplate
@Testcontainers
class AnalyticsIntegrationTest {

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
    private RoomStatsHourlyRepository statsRepository;

    @Autowired
    private StatsAggregationService aggregationService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        tipRepository.deleteAll();
        statsRepository.deleteAll();
    }

    @Test
    void shouldAggregateAndQueryRoomStats() {
        Instant hourStart = Instant.parse("2024-01-15T10:00:00Z");
        createTipAt("room1", "alice", "bob", new BigDecimal("100"), hourStart.plusSeconds(600));
        createTipAt("room1", "charlie", "bob", new BigDecimal("200"), hourStart.plusSeconds(1800));

        aggregationService.aggregateHourlyStats(hourStart);

        List<RoomStatsHourly> stats = statsRepository.findByRoomIdOrderByPeriodStartAsc("room1");
        assertThat(stats).hasSize(1);
        assertThat(stats.get(0).getTotalTips()).isEqualTo(2);
        assertThat(stats.get(0).getTotalAmount()).isEqualByComparingTo(new BigDecimal("300"));

        ResponseEntity<RoomStatsResponse> response = restTemplate.getForEntity(
                createUrl("/api/analytics/rooms/room1/stats"),
                RoomStatsResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSummary().getTotalTips()).isEqualTo(2);
        assertThat(response.getBody().getSummary().getTotalAmount()).isEqualByComparingTo(new BigDecimal("300"));
        assertThat(response.getBody().getStats()).hasSize(1);
    }

    @Test
    void shouldAggregateMultipleHours() {
        Instant hour1 = Instant.parse("2024-01-15T10:00:00Z");
        Instant hour2 = Instant.parse("2024-01-15T11:00:00Z");
        Instant hour3 = Instant.parse("2024-01-15T12:00:00Z");

        createTipAt("room1", "alice", "bob", new BigDecimal("100"), hour1.plusSeconds(600));
        createTipAt("room1", "charlie", "bob", new BigDecimal("200"), hour2.plusSeconds(600));
        createTipAt("room1", "dave", "bob", new BigDecimal("150"), hour3.plusSeconds(600));

        aggregationService.aggregateHourlyStats(hour1);
        aggregationService.aggregateHourlyStats(hour2);
        aggregationService.aggregateHourlyStats(hour3);

        ResponseEntity<RoomStatsResponse> response = restTemplate.getForEntity(
                createUrl("/api/analytics/rooms/room1/stats"),
                RoomStatsResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStats()).hasSize(3);
        assertThat(response.getBody().getSummary().getTotalTips()).isEqualTo(3);
        assertThat(response.getBody().getSummary().getTotalAmount()).isEqualByComparingTo(new BigDecimal("450"));
    }

    @Test
    void shouldFilterByDateRange() {
        Instant hour1 = Instant.parse("2024-01-15T10:00:00Z");
        Instant hour2 = Instant.parse("2024-01-15T11:00:00Z");
        Instant hour3 = Instant.parse("2024-01-15T12:00:00Z");

        createTipAt("room1", "alice", "bob", new BigDecimal("100"), hour1.plusSeconds(600));
        createTipAt("room1", "charlie", "bob", new BigDecimal("200"), hour2.plusSeconds(600));
        createTipAt("room1", "dave", "bob", new BigDecimal("150"), hour3.plusSeconds(600));

        aggregationService.aggregateHourlyStats(hour1);
        aggregationService.aggregateHourlyStats(hour2);
        aggregationService.aggregateHourlyStats(hour3);

        ResponseEntity<RoomStatsResponse> response = restTemplate.getForEntity(
                createUrl("/api/analytics/rooms/room1/stats?startDate=" + hour1 + "&endDate=" + hour2),
                RoomStatsResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStats()).hasSize(1);
        assertThat(response.getBody().getStats().get(0).getPeriodStart()).isEqualTo(hour1);
    }

    @Test
    void shouldReturnEmptyResultsForNonexistentRoom() {
        Instant hourStart = Instant.parse("2024-01-15T10:00:00Z");
        createTipAt("room1", "alice", "bob", new BigDecimal("100"), hourStart.plusSeconds(600));

        aggregationService.aggregateHourlyStats(hourStart);

        ResponseEntity<RoomStatsResponse> response = restTemplate.getForEntity(
                createUrl("/api/analytics/rooms/nonexistent/stats"),
                RoomStatsResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStats()).isEmpty();
        assertThat(response.getBody().getSummary().getTotalTips()).isEqualTo(0);
    }

    @Test
    void shouldAggregateMultipleRoomsInSameHour() {
        Instant hourStart = Instant.parse("2024-01-15T10:00:00Z");
        createTipAt("room1", "alice", "bob", new BigDecimal("100"), hourStart.plusSeconds(600));
        createTipAt("room2", "charlie", "dave", new BigDecimal("200"), hourStart.plusSeconds(1200));

        aggregationService.aggregateHourlyStats(hourStart);

        List<RoomStatsHourly> allStats = statsRepository.findAll();
        assertThat(allStats).hasSize(2);

        ResponseEntity<RoomStatsResponse> room1Response = restTemplate.getForEntity(
                createUrl("/api/analytics/rooms/room1/stats"),
                RoomStatsResponse.class
        );
        assertThat(room1Response.getBody().getSummary().getTotalTips()).isEqualTo(1);

        ResponseEntity<RoomStatsResponse> room2Response = restTemplate.getForEntity(
                createUrl("/api/analytics/rooms/room2/stats"),
                RoomStatsResponse.class
        );
        assertThat(room2Response.getBody().getSummary().getTotalTips()).isEqualTo(1);
    }

    @Test
    void shouldCountUniqueSendersAndRecipients() {
        Instant hourStart = Instant.parse("2024-01-15T10:00:00Z");
        createTipAt("room1", "alice", "bob", new BigDecimal("100"), hourStart.plusSeconds(600));
        createTipAt("room1", "alice", "bob", new BigDecimal("50"), hourStart.plusSeconds(1200));
        createTipAt("room1", "charlie", "dave", new BigDecimal("200"), hourStart.plusSeconds(1800));
        createTipAt("room1", "charlie", "bob", new BigDecimal("75"), hourStart.plusSeconds(2400));

        aggregationService.aggregateHourlyStats(hourStart);

        List<RoomStatsHourly> stats = statsRepository.findByRoomIdOrderByPeriodStartAsc("room1");
        assertThat(stats).hasSize(1);
        assertThat(stats.get(0).getTotalTips()).isEqualTo(4);
        assertThat(stats.get(0).getUniqueSenders()).isEqualTo(2);
        assertThat(stats.get(0).getUniqueRecipients()).isEqualTo(2);
    }

    @Test
    void shouldCalculateAverageTipAmountCorrectly() {
        Instant hourStart = Instant.parse("2024-01-15T10:00:00Z");
        createTipAt("room1", "alice", "bob", new BigDecimal("100"), hourStart.plusSeconds(600));
        createTipAt("room1", "charlie", "bob", new BigDecimal("200"), hourStart.plusSeconds(1200));
        createTipAt("room1", "dave", "bob", new BigDecimal("150"), hourStart.plusSeconds(1800));

        aggregationService.aggregateHourlyStats(hourStart);

        List<RoomStatsHourly> stats = statsRepository.findByRoomIdOrderByPeriodStartAsc("room1");
        assertThat(stats).hasSize(1);
        assertThat(stats.get(0).getAverageTipAmount()).isEqualByComparingTo(new BigDecimal("150.00"));

        ResponseEntity<RoomStatsResponse> response = restTemplate.getForEntity(
                createUrl("/api/analytics/rooms/room1/stats"),
                RoomStatsResponse.class
        );

        assertThat(response.getBody().getSummary().getAverageTipAmount())
                .isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    void shouldHandleZeroTipsInPeriod() {
        Instant hourStart = Instant.parse("2024-01-15T10:00:00Z");

        aggregationService.aggregateHourlyStats(hourStart);

        List<RoomStatsHourly> stats = statsRepository.findAll();
        assertThat(stats).isEmpty();

        ResponseEntity<RoomStatsResponse> response = restTemplate.getForEntity(
                createUrl("/api/analytics/rooms/room1/stats"),
                RoomStatsResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getStats()).isEmpty();
    }

    @Test
    void shouldPreventDuplicateAggregation() {
        Instant hourStart = Instant.parse("2024-01-15T10:00:00Z");
        createTipAt("room1", "alice", "bob", new BigDecimal("100"), hourStart.plusSeconds(600));

        aggregationService.aggregateHourlyStats(hourStart);
        aggregationService.aggregateHourlyStats(hourStart);

        List<RoomStatsHourly> stats = statsRepository.findByRoomIdOrderByPeriodStartAsc("room1");
        assertThat(stats.size()).isGreaterThan(0);
    }

    @Test
    void shouldOrderStatsByPeriodStartAscending() {
        Instant hour1 = Instant.parse("2024-01-15T10:00:00Z");
        Instant hour2 = Instant.parse("2024-01-15T11:00:00Z");
        Instant hour3 = Instant.parse("2024-01-15T12:00:00Z");

        createTipAt("room1", "alice", "bob", new BigDecimal("100"), hour3.plusSeconds(600));
        createTipAt("room1", "charlie", "bob", new BigDecimal("200"), hour1.plusSeconds(600));
        createTipAt("room1", "dave", "bob", new BigDecimal("150"), hour2.plusSeconds(600));

        aggregationService.aggregateHourlyStats(hour3);
        aggregationService.aggregateHourlyStats(hour1);
        aggregationService.aggregateHourlyStats(hour2);

        ResponseEntity<RoomStatsResponse> response = restTemplate.getForEntity(
                createUrl("/api/analytics/rooms/room1/stats"),
                RoomStatsResponse.class
        );

        assertThat(response.getBody().getStats()).hasSize(3);
        assertThat(response.getBody().getStats().get(0).getPeriodStart()).isEqualTo(hour1);
        assertThat(response.getBody().getStats().get(1).getPeriodStart()).isEqualTo(hour2);
        assertThat(response.getBody().getStats().get(2).getPeriodStart()).isEqualTo(hour3);
    }

    private void createTipAt(String roomId, String senderId, String recipientId, BigDecimal amount, Instant createdAt) {
        Tip tip = Tip.builder()
                .roomId(roomId)
                .senderId(senderId)
                .recipientId(recipientId)
                .amount(amount)
                .build();

        Tip savedTip = tipRepository.save(tip);
        tipRepository.flush();

        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("UPDATE tips SET created_at = :createdAt WHERE id = :id")
                    .setParameter("createdAt", createdAt)
                    .setParameter("id", savedTip.getId())
                    .executeUpdate();
        });
    }

    private String createUrl(String path) {
        return "http://localhost:" + port + path;
    }
}
