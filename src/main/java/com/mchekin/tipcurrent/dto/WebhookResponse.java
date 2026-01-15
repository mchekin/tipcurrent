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
public class WebhookResponse {

    private Long id;
    private String url;
    private String event;
    private Boolean enabled;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
}
