package com.feetfit.server.repository;

import com.feetfit.server.domain.MeasurementAnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MeasurementAnalysisStatusRepository extends JpaRepository<MeasurementAnalysisStatus, Long> {

    Optional<MeasurementAnalysisStatus> findByMeasurementSessionId(Long measurementSessionId);
}
