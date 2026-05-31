package com.feetfit.server.repository;

import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.enums.MeasurementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MeasurementSessionRepository extends JpaRepository<MeasurementSession, Long> {
    boolean existsByUserIdAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThan(
            Long userId,
            LocalDateTime startOfDay,
            LocalDateTime startOfNextDay
    );

    boolean existsByUserIdAndStatusAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThan(
            Long userId,
            MeasurementStatus status,
            LocalDateTime startOfDay,
            LocalDateTime startOfNextDay
    );

    List<MeasurementSession> findByUserIdAndStatusAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThan(
            Long userId,
            MeasurementStatus status,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    );

    // Repository - String으로 반환
    @Query(value = "SELECT DISTINCT DATE_FORMAT(ms.measured_at, '%Y-%m-%d') " +
            "FROM measurement_session ms " +
            "WHERE ms.user_id = :userId " +
            "AND YEAR(ms.measured_at) = :year " +
            "AND MONTH(ms.measured_at) = :month " +
            "AND ms.status = 'COMPLETED' " +
            "ORDER BY DATE_FORMAT(ms.measured_at, '%Y-%m-%d') ASC",
            nativeQuery = true)
    List<String> findMeasuredDatesByYearAndMonth(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("month") int month
    );
}
