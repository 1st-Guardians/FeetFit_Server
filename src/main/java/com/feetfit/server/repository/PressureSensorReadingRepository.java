package com.feetfit.server.repository;

import com.feetfit.server.domain.PressureSensorReading;
import com.feetfit.server.domain.enums.FootSide;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PressureSensorReadingRepository extends JpaRepository<PressureSensorReading, Long> {
    List<PressureSensorReading> findByMeasurementSessionIdAndFootSide(Long measurementSessionId, FootSide footSide);
}
