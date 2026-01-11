package com.mchekin.tipcurrent.service;

import com.mchekin.tipcurrent.domain.RoomStatsHourly;
import com.mchekin.tipcurrent.repository.RoomStatsHourlyRepository;
import com.mchekin.tipcurrent.repository.TipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatsAggregationService {

    private final TipRepository tipRepository;
    private final RoomStatsHourlyRepository statsRepository;

    @Transactional
    public void aggregateHourlyStats(Instant hourStart) {
        Instant hourEnd = hourStart.plus(1, ChronoUnit.HOURS);

        log.debug("Aggregating stats for period {} to {}", hourStart, hourEnd);

        List<TipRepository.RoomStatsProjection> aggregations =
                tipRepository.aggregateByRoomForPeriod(hourStart, hourEnd);

        for (TipRepository.RoomStatsProjection agg : aggregations) {
            List<RoomStatsHourly> existing = statsRepository
                    .findByRoomIdAndPeriodStartGreaterThanEqualAndPeriodStartLessThanOrderByPeriodStartAsc(
                            agg.getRoomId(), hourStart, hourEnd);

            RoomStatsHourly stats;
            if (!existing.isEmpty()) {
                stats = existing.get(0);
                stats.setPeriodEnd(hourEnd);
                stats.setTotalTips(agg.getTotalTips());
                stats.setTotalAmount(agg.getTotalAmount());
                stats.setUniqueSenders(agg.getUniqueSenders());
                stats.setUniqueRecipients(agg.getUniqueRecipients());
                stats.setAverageTipAmount(agg.getAverageTipAmount());
                log.debug("Updating existing stats for room {}", agg.getRoomId());
            } else {
                stats = RoomStatsHourly.builder()
                        .roomId(agg.getRoomId())
                        .periodStart(hourStart)
                        .periodEnd(hourEnd)
                        .totalTips(agg.getTotalTips())
                        .totalAmount(agg.getTotalAmount())
                        .uniqueSenders(agg.getUniqueSenders())
                        .uniqueRecipients(agg.getUniqueRecipients())
                        .averageTipAmount(agg.getAverageTipAmount())
                        .build();
                log.debug("Creating new stats for room {}", agg.getRoomId());
            }

            statsRepository.save(stats);
            log.debug("Aggregated stats for room {}: {} tips, total amount {}",
                    agg.getRoomId(), agg.getTotalTips(), agg.getTotalAmount());
        }

        log.info("Completed aggregation for period {} to {}: {} rooms processed",
                hourStart, hourEnd, aggregations.size());
    }
}
