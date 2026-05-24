package com.feetfit.server.repository;

import com.feetfit.server.domain.MetricAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MetricAnalysisResultRepository extends JpaRepository<MetricAnalysisResult, Long> {
}