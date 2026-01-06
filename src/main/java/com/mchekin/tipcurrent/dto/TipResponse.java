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
public class TipResponse {

    private Long id;
    private String roomId;
    private String senderId;
    private String recipientId;
    private BigDecimal amount;
    private String message;
    private String metadata;
    private Instant createdAt;
}
