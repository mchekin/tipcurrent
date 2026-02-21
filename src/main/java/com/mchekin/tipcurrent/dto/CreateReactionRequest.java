package com.mchekin.tipcurrent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReactionRequest {

    private String roomId;
    private String userId;
    private String emoji;
    private String targetId;
    private String metadata;
}
