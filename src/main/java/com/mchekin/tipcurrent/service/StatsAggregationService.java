package com.mchekin.tipcurrent.service;

import com.mchekin.tipcurrent.domain.RoomStatsHourly;
import com.mchekin.tipcurrent.repository.ReactionRepository;
import com.mchekin.tipcurrent.repository.RoomStatsHourlyRepository;
import com.mchekin.tipcurrent.repository.TipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatsAggregationService {

    private final TipRepository tipRepository;
    private final ReactionRepository reactionRepository;
    private final RoomStatsHourlyRepository statsRepository;

    @Transactional
    public void aggregateHourlyStats(Instant hourStart) {
        Instant hourEnd = hourStart.plus(1, ChronoUnit.HOURS);

        log.debug("Aggregating stats for period {} to {}", hourStart, hourEnd);

        // Aggregate tips
        List<TipRepository.RoomStatsProjection> tipAggregations =
                tipRepository.aggregateByRoomForPeriod(hourStart, hourEnd);

        // Aggregate reactions
        List<ReactionRepository.ReactionStatsProjection> reactionAggregations =
                reactionRepository.aggregateByRoomForPeriod(hourStart, hourEnd);

        // Build maps for merging
        Map<String, TipRepository.RoomStatsProjection> tipsByRoom = new HashMap<>();
        for (TipRepository.RoomStatsProjection agg : tipAggregations) {
            tipsByRoom.put(agg.getRoomId(), agg);
        }

        Map<String, ReactionRepository.ReactionStatsProjection> reactionsByRoom = new HashMap<>();
        for (ReactionRepository.ReactionStatsProjection agg : reactionAggregations) {
            reactionsByRoom.put(agg.getRoomId(), agg);
        }

        // Collect all room IDs
        Set<String> allRoomIds = new HashSet<>();
        allRoomIds.addAll(tipsByRoom.keySet());
        allRoomIds.addAll(reactionsByRoom.keySet());

        for (String roomId : allRoomIds) {
            TipRepository.RoomStatsProjection tipStats = tipsByRoom.get(roomId);
            ReactionRepository.ReactionStatsProjection reactionStats = reactionsByRoom.get(roomId);

            List<RoomStatsHourly> existing = statsRepository
                    .findByRoomIdAndPeriodStartGreaterThanEqualAndPeriodStartLessThanOrderByPeriodStartAsc(
                            roomId, hourStart, hourEnd);

            RoomStatsHourly stats;
            if (!existing.isEmpty()) {
                stats = existing.getFirst();

                stats.setPeriodEnd(hourEnd);
                updateStatsFromProjections(stats, tipStats, reactionStats);
                log.debug("Updating existing stats for room {}", roomId);
            } else {
                stats = RoomStatsHourly.builder()
                        .roomId(roomId)
                        .periodStart(hourStart)
                        .periodEnd(hourEnd)
                        .build();
                updateStatsFromProjections(stats, tipStats, reactionStats);
                log.debug("Creating new stats for room {}", roomId);
            }

            statsRepository.save(stats);
            log.debug("Aggregated stats for room {}: {} tips, {} reactions",
                    roomId,
                    tipStats != null ? tipStats.getTotalTips() : 0,
                    reactionStats != null ? reactionStats.getTotalReactions() : 0);
        }

        log.info("Completed aggregation for period {} to {}: {} rooms processed",
                hourStart, hourEnd, allRoomIds.size());
    }

    private void updateStatsFromProjections(
            RoomStatsHourly stats,
            TipRepository.RoomStatsProjection tipStats,
            ReactionRepository.ReactionStatsProjection reactionStats) {

        if (tipStats != null) {
            stats.setTotalTips(tipStats.getTotalTips());
            stats.setTotalAmount(tipStats.getTotalAmount());
            stats.setUniqueSenders(tipStats.getUniqueSenders());
            stats.setUniqueRecipients(tipStats.getUniqueRecipients());
            stats.setAverageTipAmount(tipStats.getAverageTipAmount());
        }

        if (reactionStats != null) {
            stats.setTotalReactions(reactionStats.getTotalReactions());
            stats.setUniqueReactors(reactionStats.getUniqueUsers());
        }
    }
}
