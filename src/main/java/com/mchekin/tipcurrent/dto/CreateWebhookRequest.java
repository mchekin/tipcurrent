package com.mchekin.tipcurrent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateWebhookRequest {

    private String roomId;
    private String url;
    private String event;
    private String secret;
    private String description;
}
