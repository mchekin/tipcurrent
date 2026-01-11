package com.mchekin.tipcurrent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomStatsPeriodResponse {

    private Instant periodStart;
    private Instant periodEnd;
    private Long totalTips;
    private BigDecimal totalAmount;
    private Long uniqueSenders;
    private Long uniqueRecipients;
    private BigDecimal averageTipAmount;
}
