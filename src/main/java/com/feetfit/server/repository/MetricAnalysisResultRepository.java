package com.feetfit.server.repository;

import com.feetfit.server.domain.MetricAnalysisResult;
import com.feetfit.server.domain.enums.MetricType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MetricAnalysisResultRepository extends JpaRepository<MetricAnalysisResult, Long> {

    void deleteByReportId(Long reportId);

    Optional<MetricAnalysisResult> findByReportIdAndMetricType(Long reportId, MetricType metricType);

    List<MetricAnalysisResult> findByReportId(Long reportId);

    @Query("""
            SELECT result FROM MetricAnalysisResult result
            JOIN FETCH result.report report
            WHERE report.user.id = :userId
              AND result.metricType = :metricType
              AND report.totalScore IS NOT NULL
              AND report.reportDate < :todayStart
            ORDER BY report.reportDate DESC, report.id DESC
            """)
    List<MetricAnalysisResult> findRecentCompletedBeforeToday(
            @Param("userId") Long userId,
            @Param("metricType") MetricType metricType,
            @Param("todayStart") LocalDateTime todayStart,
            Pageable pageable);
}
