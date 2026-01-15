package com.mchekin.tipcurrent.controller;

import com.mchekin.tipcurrent.domain.Webhook;
import com.mchekin.tipcurrent.domain.WebhookDeliveryLog;
import com.mchekin.tipcurrent.dto.CreateWebhookRequest;
import com.mchekin.tipcurrent.dto.WebhookResponse;
import com.mchekin.tipcurrent.repository.WebhookDeliveryLogRepository;
import com.mchekin.tipcurrent.repository.WebhookRepository;
import com.mchekin.tipcurrent.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookRepository webhookRepository;
    private final WebhookDeliveryLogRepository deliveryLogRepository;
    private final WebhookService webhookService;

    @PostMapping
    public ResponseEntity<WebhookResponse> createWebhook(@RequestBody CreateWebhookRequest request) {
        Webhook webhook = Webhook.builder()
                .roomId(request.getRoomId())
                .url(request.getUrl())
                .event(request.getEvent())
                .secret(request.getSecret())
                .description(request.getDescription())
                .enabled(true)
                .build();

        Webhook savedWebhook = webhookRepository.save(webhook);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(savedWebhook));
    }

    @GetMapping
    public ResponseEntity<List<WebhookResponse>> listWebhooks(
            @RequestParam(required = false) String roomId,
            @RequestParam(required = false, defaultValue = "false") boolean global) {

        List<Webhook> webhooks;
        if (global) {
            // List only global webhooks (roomId is null)
            webhooks = webhookRepository.findByRoomIdIsNull();
        } else if (roomId != null) {
            // List webhooks for a specific room
            webhooks = webhookRepository.findByRoomId(roomId);
        } else {
            // List all webhooks
            webhooks = webhookRepository.findAll();
        }

        List<WebhookResponse> responses = webhooks.stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<WebhookResponse> getWebhook(@PathVariable Long id) {
        return webhookRepository.findById(id)
                .map(webhook -> ResponseEntity.ok(toResponse(webhook)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWebhook(@PathVariable Long id) {
        if (!webhookRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        webhookRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/enable")
    public ResponseEntity<WebhookResponse> enableWebhook(@PathVariable Long id) {
        return webhookRepository.findById(id)
                .map(webhook -> {
                    webhook.setEnabled(true);
                    Webhook updated = webhookRepository.save(webhook);
                    return ResponseEntity.ok(toResponse(updated));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/disable")
    public ResponseEntity<WebhookResponse> disableWebhook(@PathVariable Long id) {
        return webhookRepository.findById(id)
                .map(webhook -> {
                    webhook.setEnabled(false);
                    Webhook updated = webhookRepository.save(webhook);
                    return ResponseEntity.ok(toResponse(updated));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<Void> testWebhook(@PathVariable Long id) {
        if (!webhookRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        webhookService.testWebhook(id);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{id}/deliveries")
    public ResponseEntity<Page<WebhookDeliveryLog>> getDeliveries(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (!webhookRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        Page<WebhookDeliveryLog> deliveries = deliveryLogRepository.findByWebhookIdOrderByCreatedAtDesc(
                id, PageRequest.of(page, size));

        return ResponseEntity.ok(deliveries);
    }

    private WebhookResponse toResponse(Webhook webhook) {
        return WebhookResponse.builder()
                .id(webhook.getId())
                .roomId(webhook.getRoomId())
                .url(webhook.getUrl())
                .event(webhook.getEvent())
                .enabled(webhook.getEnabled())
                .description(webhook.getDescription())
                .createdAt(webhook.getCreatedAt())
                .updatedAt(webhook.getUpdatedAt())
                .build();
    }
}
