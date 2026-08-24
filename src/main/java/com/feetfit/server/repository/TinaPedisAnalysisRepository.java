package com.feetfit.server.repository;

import com.feetfit.server.domain.TinaPedisAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TinaPedisAnalysisRepository extends JpaRepository<TinaPedisAnalysis, Long> {

    Optional<TinaPedisAnalysis> findTopByMeasurementSessionIdOrderByUpdatedAtDescIdDesc(Long measurementSessionId);

    default Optional<TinaPedisAnalysis> findByMeasurementSessionId(Long measurementSessionId) {
        return findTopByMeasurementSessionIdOrderByUpdatedAtDescIdDesc(measurementSessionId);
    }

    Optional<TinaPedisAnalysis> findTopByMeasurementSessionUserIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtDesc(
            Long userId,
            LocalDateTime startOfDay,
            LocalDateTime endOfDay
    );

    Optional<TinaPedisAnalysis> findTopByMeasurementSessionUserIdAndRecordedAtLessThanOrderByRecordedAtDesc(
            Long userId,
            LocalDateTime recordedAt
    );

    boolean existsByMeasurementSessionId(Long measurementSessionId);
}
