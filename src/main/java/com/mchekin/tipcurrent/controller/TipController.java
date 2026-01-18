package com.mchekin.tipcurrent.controller;

import com.mchekin.tipcurrent.domain.IdempotencyRecord;
import com.mchekin.tipcurrent.domain.Tip;
import com.mchekin.tipcurrent.dto.CreateTipRequest;
import com.mchekin.tipcurrent.dto.TipResponse;
import com.mchekin.tipcurrent.repository.IdempotencyRecordRepository;
import com.mchekin.tipcurrent.repository.TipRepository;
import com.mchekin.tipcurrent.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@RestController
@RequestMapping("/api/tips")
@RequiredArgsConstructor
public class TipController {

    private final TipRepository tipRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebhookService webhookService;

    @PostMapping
    public ResponseEntity<TipResponse> createTip(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CreateTipRequest request) {

        // Check for existing idempotency record
        if (idempotencyKey != null) {
            Optional<IdempotencyRecord> existing = idempotencyRepository.findById(idempotencyKey);

            if (existing.isPresent()) {
                // Return cached result
                Tip tip = tipRepository.findById(existing.get().getResourceId())
                        .orElseThrow(() -> new IllegalStateException("Tip not found for idempotency key"));
                return ResponseEntity.ok(toResponse(tip));
            }
        }

        Tip tip = Tip.builder()
                .roomId(request.getRoomId())
                .senderId(request.getSenderId())
                .recipientId(request.getRecipientId())
                .amount(request.getAmount())
                .message(request.getMessage())
                .metadata(request.getMetadata())
                .build();

        Tip savedTip = tipRepository.save(tip);

        // Save idempotency record
        if (idempotencyKey != null) {
            idempotencyRepository.save(IdempotencyRecord.builder()
                    .idempotencyKey(idempotencyKey)
                    .resourceId(savedTip.getId())
                    .resourceType("Tip")
                    .createdAt(Instant.now())
                    .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                    .build());
        }

        TipResponse response = toResponse(savedTip);

        // Broadcast tip event to WebSocket subscribers
        messagingTemplate.convertAndSend("/topic/rooms/" + savedTip.getRoomId(), response);

        // Notify webhooks asynchronously
        webhookService.notifyWebhooks(savedTip.getRoomId(), "tip.created", response);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<TipResponse>> getTips(
            @RequestParam(required = false) String roomId,
            @RequestParam(required = false) String recipientId,
            @RequestParam(required = false) String senderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Tip> tips;

        if (roomId != null && recipientId != null) {
            tips = tipRepository.findByRoomIdAndRecipientId(roomId, recipientId, pageable);
        } else if (roomId != null && senderId != null) {
            tips = tipRepository.findByRoomIdAndSenderId(roomId, senderId, pageable);
        } else if (roomId != null) {
            tips = tipRepository.findByRoomId(roomId, pageable);
        } else if (recipientId != null) {
            tips = tipRepository.findByRecipientId(recipientId, pageable);
        } else if (senderId != null) {
            tips = tipRepository.findBySenderId(senderId, pageable);
        } else {
            tips = tipRepository.findAll(pageable);
        }

        Page<TipResponse> response = tips.map(this::toResponse);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TipResponse> getTipById(@PathVariable Long id) {
        Optional<Tip> tip = tipRepository.findById(id);

        if (tip.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(toResponse(tip.get()));
    }

    private TipResponse toResponse(Tip tip) {
        return TipResponse.builder()
                .id(tip.getId())
                .roomId(tip.getRoomId())
                .senderId(tip.getSenderId())
                .recipientId(tip.getRecipientId())
                .amount(tip.getAmount())
                .message(tip.getMessage())
                .metadata(tip.getMetadata())
                .createdAt(tip.getCreatedAt())
                .build();
    }
}
