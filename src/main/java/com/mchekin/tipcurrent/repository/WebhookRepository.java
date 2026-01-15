package com.mchekin.tipcurrent.repository;

import com.mchekin.tipcurrent.domain.Webhook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WebhookRepository extends JpaRepository<Webhook, Long> {

    List<Webhook> findByRoomIdAndEventAndEnabledTrue(String roomId, String event);

    List<Webhook> findByRoomIdIsNullAndEventAndEnabledTrue(String event);

    List<Webhook> findByRoomId(String roomId);

    List<Webhook> findByRoomIdIsNull();
}
