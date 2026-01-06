package com.mchekin.tipcurrent.controller;

import com.mchekin.tipcurrent.domain.Tip;
import com.mchekin.tipcurrent.dto.CreateTipRequest;
import com.mchekin.tipcurrent.dto.TipResponse;
import com.mchekin.tipcurrent.repository.TipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tips")
@RequiredArgsConstructor
public class TipController {

    private final TipRepository tipRepository;

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

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
