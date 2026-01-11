package com.mchekin.tipcurrent.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "room_stats_hourly",
    indexes = {
        @Index(name = "idx_room_period", columnList = "roomId,periodStart"),
        @Index(name = "idx_period", columnList = "periodStart")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_room_period", columnNames = {"roomId", "periodStart"})
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomStatsHourly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String roomId;

    @Column(nullable = false)
    private Instant periodStart;

    @Column(nullable = false)
    private Instant periodEnd;

    @Column(nullable = false)
    private Long totalTips;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private Long uniqueSenders;

    @Column(nullable = false)
    private Long uniqueRecipients;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal averageTipAmount;

    @Column(nullable = false, updatable = false)
    private Instant lastAggregatedAt;

    @PrePersist
    protected void onCreate() {
        if (lastAggregatedAt == null) {
            lastAggregatedAt = Instant.now();
        }
    }
}
