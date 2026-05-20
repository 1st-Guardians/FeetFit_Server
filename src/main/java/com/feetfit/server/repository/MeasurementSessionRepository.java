package com.feetfit.server.repository;

import com.feetfit.server.domain.MeasurementSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface MeasurementSessionRepository extends JpaRepository<MeasurementSession, Long> {
    boolean existsByUserIdAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThan(
            Long userId,
            LocalDateTime startOfDay,
            LocalDateTime startOfNextDay
    );
}
