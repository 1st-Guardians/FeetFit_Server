package com.feetfit.server.repository;

import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.enums.MeasurementStatus;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
