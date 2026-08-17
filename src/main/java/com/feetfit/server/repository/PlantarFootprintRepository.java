package com.feetfit.server.repository;

import com.feetfit.server.domain.PlantarFootprint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlantarFootprintRepository extends JpaRepository<PlantarFootprint, Long> {

    Optional<PlantarFootprint> findTopByMeasurementSessionIdOrderByRecordedAtDesc(Long measurementSessionId);
}
