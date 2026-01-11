package com.mchekin.tipcurrent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomStatsResponse {

    private String roomId;
    private List<RoomStatsPeriodResponse> stats;
    private RoomStatsSummary summary;
}
