package com.mchekin.tipcurrent.repository;

import com.mchekin.tipcurrent.domain.Tip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TipRepository extends JpaRepository<Tip, Long> {

    Page<Tip> findByRoomId(String roomId, Pageable pageable);

    Page<Tip> findByRecipientId(String recipientId, Pageable pageable);

    Page<Tip> findBySenderId(String senderId, Pageable pageable);

    Page<Tip> findByRoomIdAndRecipientId(String roomId, String recipientId, Pageable pageable);

    Page<Tip> findByRoomIdAndSenderId(String roomId, String senderId, Pageable pageable);
}