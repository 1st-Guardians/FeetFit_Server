package com.feetfit.server.repository;

import com.feetfit.server.domain.HalluxValgusAnalysis;
import com.feetfit.server.domain.MeasurementSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HalluxValgusAnalysisRepository extends JpaRepository<HalluxValgusAnalysis, Long> {

    Optional<HalluxValgusAnalysis> findByMeasurementSession(MeasurementSession measurementSession);
}