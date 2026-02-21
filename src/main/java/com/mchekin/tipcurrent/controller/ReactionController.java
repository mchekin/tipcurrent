package com.mchekin.tipcurrent.controller;

import com.mchekin.tipcurrent.domain.IdempotencyRecord;
import com.mchekin.tipcurrent.domain.Reaction;
import com.mchekin.tipcurrent.dto.CreateReactionRequest;
import com.mchekin.tipcurrent.dto.ReactionResponse;
import com.mchekin.tipcurrent.repository.IdempotencyRecordRepository;
import com.mchekin.tipcurrent.repository.ReactionRepository;
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
@RequestMapping("/api/reactions")
@RequiredArgsConstructor
public class ReactionController {

    private final ReactionRepository reactionRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebhookService webhookService;

    @PostMapping
    public ResponseEntity<ReactionResponse> createReaction(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CreateReactionRequest request) {

        // Check for existing idempotency record
        if (idempotencyKey != null) {
            Optional<IdempotencyRecord> existing = idempotencyRepository.findById(idempotencyKey);

            if (existing.isPresent()) {
                // Return cached result
                Reaction reaction = reactionRepository.findById(existing.get().getResourceId())
                        .orElseThrow(() -> new IllegalStateException("Reaction not found for idempotency key"));
                return ResponseEntity.ok(toResponse(reaction));
            }
        }

        Reaction reaction = Reaction.builder()
                .roomId(request.getRoomId())
                .userId(request.getUserId())
                .emoji(request.getEmoji())
                .targetId(request.getTargetId())
                .metadata(request.getMetadata())
                .build();

        Reaction savedReaction = reactionRepository.save(reaction);

        // Save idempotency record
        if (idempotencyKey != null) {
            idempotencyRepository.save(IdempotencyRecord.builder()
                    .idempotencyKey(idempotencyKey)
                    .resourceId(savedReaction.getId())
                    .resourceType("Reaction")
                    .createdAt(Instant.now())
                    .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                    .build());
        }

        ReactionResponse response = toResponse(savedReaction);

        // Broadcast reaction event to WebSocket subscribers
        messagingTemplate.convertAndSend("/topic/rooms/" + savedReaction.getRoomId(), response);

        // Notify webhooks asynchronously
        webhookService.notifyWebhooks(savedReaction.getRoomId(), "reaction.created", response);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<ReactionResponse>> getReactions(
            @RequestParam(required = false) String roomId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String emoji,
            @RequestParam(required = false) String targetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Reaction> reactions;

        if (roomId != null && userId != null) {
            reactions = reactionRepository.findByRoomIdAndUserId(roomId, userId, pageable);
        } else if (roomId != null && emoji != null) {
            reactions = reactionRepository.findByRoomIdAndEmoji(roomId, emoji, pageable);
        } else if (roomId != null) {
            reactions = reactionRepository.findByRoomId(roomId, pageable);
        } else if (userId != null) {
            reactions = reactionRepository.findByUserId(userId, pageable);
        } else if (targetId != null) {
            reactions = reactionRepository.findByTargetId(targetId, pageable);
        } else {
            reactions = reactionRepository.findAll(pageable);
        }

        Page<ReactionResponse> response = reactions.map(this::toResponse);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReactionResponse> getReactionById(@PathVariable Long id) {
        Optional<Reaction> reaction = reactionRepository.findById(id);

        return reaction.map(value -> ResponseEntity.ok(toResponse(value)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ReactionResponse toResponse(Reaction reaction) {
        return ReactionResponse.builder()
                .id(reaction.getId())
                .roomId(reaction.getRoomId())
                .userId(reaction.getUserId())
                .emoji(reaction.getEmoji())
                .targetId(reaction.getTargetId())
                .metadata(reaction.getMetadata())
                .createdAt(reaction.getCreatedAt())
                .build();
    }
}
