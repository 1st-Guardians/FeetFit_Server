package com.feetfit.server.repository;

import com.feetfit.server.domain.DailyFootAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface DailyFootAnalysisRepository extends JpaRepository<DailyFootAnalysis, Long> {

    Optional<DailyFootAnalysis> findByMeasurementSessionId(Long measurementSessionId);

    // 이전 측정 데이터 조회
    Optional<DailyFootAnalysis> findTopByMeasurementSessionUserIdAndCreatedAtLessThanOrderByCreatedAtDesc(
            Long userId, LocalDateTime startOfDay);

    // 해당 날짜 가장 최근 데이터 조회
    Optional<DailyFootAnalysis> findTopByMeasurementSessionUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            Long userId,
            LocalDateTime startOfDay,
            LocalDateTime endOfDay);

    // 사용자의 발타입 조회
    Optional<DailyFootAnalysis> findTopByMeasurementSessionUserIdOrderByCreatedAtDesc(Long userId);
}