package com.mchekin.tipcurrent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactionResponse {

    private Long id;
    private String roomId;
    private String userId;
    private String emoji;
    private String targetId;
    private String metadata;
    private Instant createdAt;
}
