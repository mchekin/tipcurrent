package com.mchekin.tipcurrent.scheduler;

import com.mchekin.tipcurrent.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyCleanupScheduler {

    private final IdempotencyRecordRepository idempotencyRepository;

    @Scheduled(cron = "0 0 * * * *")  // Every hour at minute 0
    public void cleanupExpiredRecords() {
        Instant now = Instant.now();
        log.info("Starting cleanup of expired idempotency records older than {}", now);

        idempotencyRepository.deleteByExpiresAtBefore(now);

        log.info("Completed cleanup of expired idempotency records");
    }
}
