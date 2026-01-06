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
public class CreateTipRequest {

    private String roomId;
    private String senderId;
    private String recipientId;
    private BigDecimal amount;
    private String message;
    private String metadata;
}
