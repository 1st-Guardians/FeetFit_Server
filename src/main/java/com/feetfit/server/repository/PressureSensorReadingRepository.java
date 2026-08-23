package com.feetfit.server.repository;

import com.feetfit.server.domain.PressureSensorReading;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PressureSensorReadingRepository extends JpaRepository<PressureSensorReading, Long> {
    List<PressureSensorReading> findByMeasurementSessionIdOrderByFootSideAscSensorIndexAsc(Long measurementSessionId);
}
