package com.feetfit.server.repository;

import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.enums.MeasurementStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MeasurementSessionRepository extends JpaRepository<MeasurementSession, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MeasurementSession m " +
            "JOIN FETCH m.user " +
            "JOIN FETCH m.device " +
            "WHERE m.id = :measurementSessionId")
    Optional<MeasurementSession> findByIdForCompletion(
            @Param("measurementSessionId") Long measurementSessionId
    );

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
