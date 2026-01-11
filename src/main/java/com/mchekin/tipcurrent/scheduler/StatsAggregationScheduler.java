package com.mchekin.tipcurrent.scheduler;

import com.mchekin.tipcurrent.service.StatsAggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class StatsAggregationScheduler {

    private final StatsAggregationService aggregationService;

    @Scheduled(cron = "0 5 * * * *")
    public void aggregateLastHour() {
        Instant now = Instant.now();
        Instant lastHourStart = now.minus(1, ChronoUnit.HOURS)
                .truncatedTo(ChronoUnit.HOURS);

        log.info("Starting scheduled aggregation for hour: {}", lastHourStart);
        aggregationService.aggregateHourlyStats(lastHourStart);
    }

    public void backfillHistoricalStats(Instant fromDate, Instant toDate) {
        log.info("Starting backfill from {} to {}", fromDate, toDate);
        Instant current = fromDate.truncatedTo(ChronoUnit.HOURS);
        int hoursProcessed = 0;

        while (current.isBefore(toDate)) {
            aggregationService.aggregateHourlyStats(current);
            current = current.plus(1, ChronoUnit.HOURS);
            hoursProcessed++;
        }

        log.info("Backfill completed: {} hours processed", hoursProcessed);
    }
}
