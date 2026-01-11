package com.mchekin.tipcurrent.controller;

import com.mchekin.tipcurrent.domain.RoomStatsHourly;
import com.mchekin.tipcurrent.dto.RoomStatsPeriodResponse;
import com.mchekin.tipcurrent.dto.RoomStatsResponse;
import com.mchekin.tipcurrent.dto.RoomStatsSummary;
import com.mchekin.tipcurrent.repository.RoomStatsHourlyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final RoomStatsHourlyRepository statsRepository;

    @GetMapping("/rooms/{roomId}/stats")
    public ResponseEntity<RoomStatsResponse> getRoomStats(
            @PathVariable String roomId,
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate) {

        List<RoomStatsHourly> hourlyStats;

        if (startDate != null && endDate != null) {
            hourlyStats = statsRepository.findByRoomIdAndPeriodStartGreaterThanEqualAndPeriodStartLessThanOrderByPeriodStartAsc(
                    roomId, startDate, endDate
            );
        } else {
            hourlyStats = statsRepository.findByRoomIdOrderByPeriodStartAsc(roomId);
        }

        List<RoomStatsPeriodResponse> periods = hourlyStats.stream()
                .map(this::toDto)
                .toList();

        RoomStatsSummary summary = calculateSummary(hourlyStats);

        RoomStatsResponse response = RoomStatsResponse.builder()
                .roomId(roomId)
                .stats(periods)
                .summary(summary)
                .build();

        return ResponseEntity.ok(response);
    }

    private RoomStatsPeriodResponse toDto(RoomStatsHourly stats) {
        return RoomStatsPeriodResponse.builder()
                .periodStart(stats.getPeriodStart())
                .periodEnd(stats.getPeriodEnd())
                .totalTips(stats.getTotalTips())
                .totalAmount(stats.getTotalAmount())
                .uniqueSenders(stats.getUniqueSenders())
                .uniqueRecipients(stats.getUniqueRecipients())
                .averageTipAmount(stats.getAverageTipAmount())
                .build();
    }

    private RoomStatsSummary calculateSummary(List<RoomStatsHourly> stats) {
        long totalTips = stats.stream().mapToLong(RoomStatsHourly::getTotalTips).sum();

        BigDecimal totalAmount = stats.stream()
                .map(RoomStatsHourly::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgAmount = totalTips > 0
                ? totalAmount.divide(BigDecimal.valueOf(totalTips), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return RoomStatsSummary.builder()
                .totalTips(totalTips)
                .totalAmount(totalAmount)
                .averageTipAmount(avgAmount)
                .build();
    }
}
