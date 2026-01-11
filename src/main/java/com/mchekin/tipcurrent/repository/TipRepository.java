package com.mchekin.tipcurrent.repository;

import com.mchekin.tipcurrent.domain.Tip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Repository
public interface TipRepository extends JpaRepository<Tip, Long> {

    Page<Tip> findByRoomId(String roomId, Pageable pageable);

    Page<Tip> findByRecipientId(String recipientId, Pageable pageable);

    Page<Tip> findBySenderId(String senderId, Pageable pageable);

    Page<Tip> findByRoomIdAndRecipientId(String roomId, String recipientId, Pageable pageable);

    Page<Tip> findByRoomIdAndSenderId(String roomId, String senderId, Pageable pageable);

    @Query(value = """
        SELECT
            room_id as roomId,
            COUNT(*) as totalTips,
            SUM(amount) as totalAmount,
            COUNT(DISTINCT sender_id) as uniqueSenders,
            COUNT(DISTINCT recipient_id) as uniqueRecipients,
            AVG(amount) as averageTipAmount
        FROM tips
        WHERE created_at >= :periodStart AND created_at < :periodEnd
        GROUP BY room_id
        """, nativeQuery = true)
    List<RoomStatsProjection> aggregateByRoomForPeriod(
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd
    );

    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "UPDATE tips SET created_at = :createdAt WHERE id = :id", nativeQuery = true)
    void updateCreatedAt(@Param("id") Long id, @Param("createdAt") Instant createdAt);

    interface RoomStatsProjection {
        String getRoomId();
        Long getTotalTips();
        BigDecimal getTotalAmount();
        Long getUniqueSenders();
        Long getUniqueRecipients();
        BigDecimal getAverageTipAmount();
    }
}