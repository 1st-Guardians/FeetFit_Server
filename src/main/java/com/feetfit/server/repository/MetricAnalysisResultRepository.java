package com.feetfit.server.repository;

import com.feetfit.server.domain.MetricAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetricAnalysisResultRepository extends JpaRepository<MetricAnalysisResult, Long> {
}