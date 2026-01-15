package com.mchekin.tipcurrent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mchekin.tipcurrent.domain.Webhook;
import com.mchekin.tipcurrent.domain.WebhookDeliveryLog;
import com.mchekin.tipcurrent.repository.WebhookDeliveryLogRepository;
import com.mchekin.tipcurrent.repository.WebhookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final WebhookRepository webhookRepository;
    private final WebhookDeliveryLogRepository deliveryLogRepository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Async
    public void notifyWebhooks(String event, Object payload) {
        List<Webhook> webhooks = webhookRepository.findByEventAndEnabledTrue(event);

        log.info("Notifying {} webhooks for event: {}", webhooks.size(), event);

        for (Webhook webhook : webhooks) {
            deliverWebhook(webhook, event, payload, 1);
        }
    }

    public void testWebhook(Long webhookId) {
        Webhook webhook = webhookRepository.findById(webhookId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook not found: " + webhookId));

        Object testPayload = new TestWebhookPayload("test.event", "This is a test webhook delivery");

        deliverWebhook(webhook, "test.event", testPayload, 1);
    }

    private void deliverWebhook(Webhook webhook, String event, Object payload, int attemptNumber) {
        long startTime = System.currentTimeMillis();

        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            String signature = calculateHMAC(jsonPayload, webhook.getSecret());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhook.getUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("X-TipCurrent-Signature", signature)
                    .header("X-TipCurrent-Event", event)
                    .header("X-TipCurrent-Delivery-Attempt", String.valueOf(attemptNumber))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long durationMs = System.currentTimeMillis() - startTime;

            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;

            logDelivery(webhook.getId(), event, success, response.statusCode(),
                    response.body(), null, attemptNumber, durationMs);

            if (success) {
                log.info("Webhook delivered successfully: webhookId={}, event={}, status={}",
                        webhook.getId(), event, response.statusCode());
            } else {
                log.warn("Webhook delivery failed: webhookId={}, event={}, status={}, attempt={}",
                        webhook.getId(), event, response.statusCode(), attemptNumber);

                // Simple retry logic: retry once after 5 seconds
                if (attemptNumber == 1) {
                    retryWebhook(webhook, event, payload, attemptNumber + 1);
                }
            }

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;

            log.error("Webhook delivery exception: webhookId={}, event={}, attempt={}",
                    webhook.getId(), event, attemptNumber, e);

            logDelivery(webhook.getId(), event, false, 0, null,
                    e.getMessage(), attemptNumber, durationMs);

            // Simple retry logic: retry once after 5 seconds
            if (attemptNumber == 1) {
                retryWebhook(webhook, event, payload, attemptNumber + 1);
            }
        }
    }

    private void retryWebhook(Webhook webhook, String event, Object payload, int attemptNumber) {
        try {
            Thread.sleep(5000);  // Wait 5 seconds before retry
            deliverWebhook(webhook, event, payload, attemptNumber);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Webhook retry interrupted: webhookId={}, event={}", webhook.getId(), event);
        }
    }

    private String calculateHMAC(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception e) {
            log.error("Failed to calculate HMAC", e);
            throw new RuntimeException("Failed to calculate HMAC", e);
        }
    }

    private void logDelivery(Long webhookId, String event, boolean success, int httpStatusCode,
                             String responseBody, String errorMessage, int attemptNumber, long durationMs) {
        WebhookDeliveryLog deliveryLog = WebhookDeliveryLog.builder()
                .webhookId(webhookId)
                .event(event)
                .success(success)
                .httpStatusCode(httpStatusCode)
                .responseBody(responseBody != null && responseBody.length() > 1000
                        ? responseBody.substring(0, 1000) : responseBody)
                .errorMessage(errorMessage)
                .attemptNumber(attemptNumber)
                .durationMs(durationMs)
                .build();

        deliveryLogRepository.save(deliveryLog);
    }

    private record TestWebhookPayload(String event, String message) {
    }
}
