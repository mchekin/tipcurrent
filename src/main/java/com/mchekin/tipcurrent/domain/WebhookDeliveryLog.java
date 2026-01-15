package com.mchekin.tipcurrent.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "webhook_delivery_logs",
    indexes = {
        @Index(name = "idx_webhook_delivery_webhook_id", columnList = "webhookId"),
        @Index(name = "idx_webhook_delivery_created_at", columnList = "createdAt")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookDeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long webhookId;

    @Column(nullable = false, length = 100)
    private String event;

    @Column(nullable = false)
    private Boolean success;

    @Column(nullable = false)
    private Integer httpStatusCode;

    @Column(columnDefinition = "TEXT")
    private String responseBody;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private Integer attemptNumber;

    @Column(nullable = false)
    private Long durationMs;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
