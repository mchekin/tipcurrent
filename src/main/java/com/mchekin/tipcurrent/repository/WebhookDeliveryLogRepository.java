package com.mchekin.tipcurrent.repository;

import com.mchekin.tipcurrent.domain.WebhookDeliveryLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookDeliveryLogRepository extends JpaRepository<WebhookDeliveryLog, Long> {

    Page<WebhookDeliveryLog> findByWebhookIdOrderByCreatedAtDesc(Long webhookId, Pageable pageable);
}
