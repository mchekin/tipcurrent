package com.mchekin.tipcurrent.repository;

import com.mchekin.tipcurrent.domain.Webhook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WebhookRepository extends JpaRepository<Webhook, Long> {

    List<Webhook> findByEventAndEnabledTrue(String event);

    List<Webhook> findByEnabledTrue();
}
