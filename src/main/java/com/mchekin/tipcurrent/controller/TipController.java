package com.mchekin.tipcurrent.controller;

import com.mchekin.tipcurrent.domain.Tip;
import com.mchekin.tipcurrent.dto.CreateTipRequest;
import com.mchekin.tipcurrent.dto.TipResponse;
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

import java.util.Optional;

@RestController
@RequestMapping("/api/tips")
@RequiredArgsConstructor
public class TipController {

    private final TipRepository tipRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebhookService webhookService;

    @PostMapping
    public ResponseEntity<TipResponse> createTip(@RequestBody CreateTipRequest request) {
        Tip tip = Tip.builder()
                .roomId(request.getRoomId())
                .senderId(request.getSenderId())
                .recipientId(request.getRecipientId())
                .amount(request.getAmount())
                .message(request.getMessage())
                .metadata(request.getMetadata())
                .build();

        Tip savedTip = tipRepository.save(tip);

        TipResponse response = TipResponse.builder()
                .id(savedTip.getId())
                .roomId(savedTip.getRoomId())
                .senderId(savedTip.getSenderId())
                .recipientId(savedTip.getRecipientId())
                .amount(savedTip.getAmount())
                .message(savedTip.getMessage())
                .metadata(savedTip.getMetadata())
                .createdAt(savedTip.getCreatedAt())
                .build();

        // Broadcast tip event to WebSocket subscribers
        messagingTemplate.convertAndSend("/topic/rooms/" + savedTip.getRoomId(), response);

        // Notify webhooks asynchronously
        webhookService.notifyWebhooks("tip.created", response);

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

        Page<TipResponse> response = tips.map(tip -> TipResponse.builder()
                .id(tip.getId())
                .roomId(tip.getRoomId())
                .senderId(tip.getSenderId())
                .recipientId(tip.getRecipientId())
                .amount(tip.getAmount())
                .message(tip.getMessage())
                .metadata(tip.getMetadata())
                .createdAt(tip.getCreatedAt())
                .build()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TipResponse> getTipById(@PathVariable Long id) {
        Optional<Tip> tip = tipRepository.findById(id);

        if (tip.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        TipResponse response = TipResponse.builder()
                .id(tip.get().getId())
                .roomId(tip.get().getRoomId())
                .senderId(tip.get().getSenderId())
                .recipientId(tip.get().getRecipientId())
                .amount(tip.get().getAmount())
                .message(tip.get().getMessage())
                .metadata(tip.get().getMetadata())
                .createdAt(tip.get().getCreatedAt())
                .build();

        return ResponseEntity.ok(response);
    }
}
