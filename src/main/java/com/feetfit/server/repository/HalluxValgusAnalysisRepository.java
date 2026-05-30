package com.feetfit.server.repository;

import com.feetfit.server.domain.HalluxValgusAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface HalluxValgusAnalysisRepository extends JpaRepository<HalluxValgusAnalysis, Long> {

    @Query("SELECT h FROM HalluxValgusAnalysis h " +
            "WHERE h.measurementSession.user.id = :userId " +
            "AND DATE(h.createdAt) = DATE(:date)")
    Optional<HalluxValgusAnalysis> findByUserIdAndDate(
            @Param("userId") Long userId,
            @Param("date") LocalDateTime date);

    // 해당 날짜의 가장 최근 데이터
    Optional<HalluxValgusAnalysis> findTopByMeasurementSessionUserIdAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThanOrderByUpdatedAtDesc(
            Long userId,
            LocalDateTime startOfDay,
            LocalDateTime endOfDay);

    // 해당 날짜 이전의 가장 최근 데이터
    Optional<HalluxValgusAnalysis> findTopByMeasurementSessionUserIdAndUpdatedAtLessThanOrderByUpdatedAtDesc(
            Long userId,
            LocalDateTime startOfDay);

    Optional<HalluxValgusAnalysis> findByMeasurementSessionId(Long measurementSessionId);

}