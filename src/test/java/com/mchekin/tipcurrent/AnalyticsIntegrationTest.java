package com.mchekin.tipcurrent;

import com.mchekin.tipcurrent.domain.RoomStatsHourly;
import com.mchekin.tipcurrent.dto.RoomStatsResponse;
import com.mchekin.tipcurrent.repository.RoomStatsHourlyRepository;
import com.mchekin.tipcurrent.repository.TipRepository;
import com.mchekin.tipcurrent.service.StatsAggregationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
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
    private JdbcTemplate jdbcTemplate;

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
        assertThat(stats.getFirst().getTotalTips()).isEqualTo(2);
        assertThat(stats.getFirst().getTotalAmount()).isEqualByComparingTo(new BigDecimal("300"));

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
    void shouldRetrieveAllHoursWithoutDateFiltering() {
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
    void shouldFilterToSingleHourWithExclusiveEndDate() {
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
        assertThat(response.getBody().getStats().getFirst().getPeriodStart()).isEqualTo(hour1);
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
        assertThat(stats.getFirst().getTotalTips()).isEqualTo(4);
        assertThat(stats.getFirst().getUniqueSenders()).isEqualTo(2);
        assertThat(stats.getFirst().getUniqueRecipients()).isEqualTo(2);
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
        assertThat(stats.getFirst().getAverageTipAmount()).isEqualByComparingTo(new BigDecimal("150.00"));

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
    void shouldUpsertOnDuplicateAggregation() {
        Instant hourStart = Instant.parse("2024-01-15T10:00:00Z");
        createTipAt("room1", "alice", "bob", new BigDecimal("100"), hourStart.plusSeconds(600));

        aggregationService.aggregateHourlyStats(hourStart);

        List<RoomStatsHourly> statsAfterFirst = statsRepository.findByRoomIdOrderByPeriodStartAsc("room1");
        assertThat(statsAfterFirst).hasSize(1);
        assertThat(statsAfterFirst.getFirst().getTotalTips()).isEqualTo(1);
        assertThat(statsAfterFirst.getFirst().getTotalAmount()).isEqualByComparingTo(new BigDecimal("100"));

        aggregationService.aggregateHourlyStats(hourStart);

        List<RoomStatsHourly> statsAfterSecond = statsRepository.findByRoomIdOrderByPeriodStartAsc("room1");
        assertThat(statsAfterSecond).hasSize(1);
        assertThat(statsAfterSecond.getFirst().getId()).isEqualTo(statsAfterFirst.getFirst().getId());
        assertThat(statsAfterSecond.getFirst().getTotalTips()).isEqualTo(1);
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
        assertThat(response.getBody().getStats().getFirst().getPeriodStart()).isEqualTo(hour1);
        assertThat(response.getBody().getStats().get(1).getPeriodStart()).isEqualTo(hour2);
        assertThat(response.getBody().getStats().getLast().getPeriodStart()).isEqualTo(hour3);
    }

    @Test
    void shouldHandleTipsAtHourBoundaries() {
        Instant hourStart = Instant.parse("2024-01-15T10:00:00Z");
        Instant hourEnd = Instant.parse("2024-01-15T11:00:00Z");

        createTipAt("room1", "alice", "bob", new BigDecimal("100"), hourStart);
        createTipAt("room1", "charlie", "bob", new BigDecimal("200"), hourStart.plusSeconds(1));
        createTipAt("room1", "dave", "bob", new BigDecimal("150"), hourEnd.minusSeconds(1));
        createTipAt("room1", "eve", "bob", new BigDecimal("50"), hourEnd);

        aggregationService.aggregateHourlyStats(hourStart);

        List<RoomStatsHourly> stats = statsRepository.findByRoomIdOrderByPeriodStartAsc("room1");
        assertThat(stats).hasSize(1);
        assertThat(stats.getFirst().getTotalTips()).isEqualTo(3);
        assertThat(stats.getFirst().getTotalAmount()).isEqualByComparingTo(new BigDecimal("450"));
    }

    @Test
    void shouldReaggregateWithNewData() {
        Instant hourStart = Instant.parse("2024-01-15T10:00:00Z");
        createTipAt("room1", "alice", "bob", new BigDecimal("100"), hourStart.plusSeconds(600));

        aggregationService.aggregateHourlyStats(hourStart);

        List<RoomStatsHourly> statsAfterFirst = statsRepository.findByRoomIdOrderByPeriodStartAsc("room1");
        assertThat(statsAfterFirst.getFirst().getTotalTips()).isEqualTo(1);
        assertThat(statsAfterFirst.getFirst().getTotalAmount()).isEqualByComparingTo(new BigDecimal("100"));

        createTipAt("room1", "charlie", "bob", new BigDecimal("200"), hourStart.plusSeconds(1800));

        aggregationService.aggregateHourlyStats(hourStart);

        List<RoomStatsHourly> statsAfterSecond = statsRepository.findByRoomIdOrderByPeriodStartAsc("room1");
        assertThat(statsAfterSecond).hasSize(1);
        assertThat(statsAfterSecond.getFirst().getTotalTips()).isEqualTo(2);
        assertThat(statsAfterSecond.getFirst().getTotalAmount()).isEqualByComparingTo(new BigDecimal("300"));
        assertThat(statsAfterSecond.getFirst().getAverageTipAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    void shouldFilterToMultipleHoursAndCalculateSummary() {
        Instant hour1 = Instant.parse("2024-01-15T10:00:00Z");
        Instant hour2 = Instant.parse("2024-01-15T11:00:00Z");
        Instant hour3 = Instant.parse("2024-01-15T12:00:00Z");

        createTipAt("room1", "alice", "bob", new BigDecimal("100"), hour1.plusSeconds(600));
        createTipAt("room1", "charlie", "bob", new BigDecimal("200"), hour2.plusSeconds(600));
        createTipAt("room1", "dave", "bob", new BigDecimal("300"), hour2.plusSeconds(1200));
        createTipAt("room1", "eve", "bob", new BigDecimal("400"), hour3.plusSeconds(600));

        aggregationService.aggregateHourlyStats(hour1);
        aggregationService.aggregateHourlyStats(hour2);
        aggregationService.aggregateHourlyStats(hour3);

        ResponseEntity<RoomStatsResponse> response = restTemplate.getForEntity(
                createUrl("/api/analytics/rooms/room1/stats?startDate=" + hour1 + "&endDate=" + hour3),
                RoomStatsResponse.class
        );

        assertThat(response.getBody().getStats()).hasSize(2);
        assertThat(response.getBody().getSummary().getTotalTips()).isEqualTo(3);
        assertThat(response.getBody().getSummary().getTotalAmount()).isEqualByComparingTo(new BigDecimal("600"));
        assertThat(response.getBody().getSummary().getAverageTipAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    private void createTipAt(String roomId, String senderId, String recipientId, BigDecimal amount, Instant createdAt) {
        jdbcTemplate.update(
                "INSERT INTO tips (room_id, sender_id, recipient_id, amount, created_at) VALUES (?, ?, ?, ?, ?)",
                roomId, senderId, recipientId, amount, Timestamp.from(createdAt)
        );
    }

    private String createUrl(String path) {
        return "http://localhost:" + port + path;
    }
}
