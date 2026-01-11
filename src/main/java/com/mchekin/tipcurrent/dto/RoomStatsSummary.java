package com.mchekin.tipcurrent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomStatsSummary {

    private Long totalTips;
    private BigDecimal totalAmount;
    private BigDecimal averageTipAmount;
}
