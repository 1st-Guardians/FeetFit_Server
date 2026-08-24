package com.feetfit.server.repository;

import com.feetfit.server.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    // 날짜 기준 가장 최근 리포트 조회
    Optional<Report> findTopByUserIdAndReportDateGreaterThanEqualAndReportDateLessThanOrderByReportDateDesc(
            Long userId,
            LocalDateTime startOfDay,
            LocalDateTime endOfDay);

    // 가장 최근 리포트 조회
    Optional<Report> findTopByUserIdOrderByReportDateDesc(Long userId);

    // 1년간 월별 평균 점수 조회
    @Query("SELECT r FROM Report r " +
            "WHERE r.user.id = :userId " +
            "AND r.reportDate >= :startDate " +
            "AND r.reportDate < :endDate " +
            "ORDER BY r.reportDate ASC")
    List<Report> findByUserIdAndReportDateBetween(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    Optional<Report> findTopByMeasurementSessionIdOrderByReportDateDescIdDesc(Long measurementSessionId);

    default Optional<Report> findByMeasurementSessionId(Long measurementSessionId) {
        return findTopByMeasurementSessionIdOrderByReportDateDescIdDesc(measurementSessionId);
    }
}
