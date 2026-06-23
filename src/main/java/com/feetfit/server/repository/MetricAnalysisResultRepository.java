package com.feetfit.server.repository;

import com.feetfit.server.domain.MetricAnalysisResult;
import com.feetfit.server.domain.enums.MetricType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MetricAnalysisResultRepository extends JpaRepository<MetricAnalysisResult, Long> {

    void deleteByReportId(Long reportId);

    Optional<MetricAnalysisResult> findByReportIdAndMetricType(Long reportId, MetricType metricType);

    List<MetricAnalysisResult> findByReportId(Long reportId);
}
