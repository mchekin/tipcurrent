package com.mchekin.tipcurrent.repository;

import com.mchekin.tipcurrent.domain.RoomStatsHourly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface RoomStatsHourlyRepository extends JpaRepository<RoomStatsHourly, Long> {

    List<RoomStatsHourly> findByRoomIdAndPeriodStartGreaterThanEqualAndPeriodStartLessThanOrderByPeriodStartAsc(
            String roomId,
            Instant startDate,
            Instant endDate
    );

    List<RoomStatsHourly> findByRoomIdOrderByPeriodStartAsc(String roomId);
}
