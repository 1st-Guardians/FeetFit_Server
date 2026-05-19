package com.feetfit.server.repository;

import com.feetfit.server.domain.PressureSensorReading;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PressureSensorReadingRepository extends JpaRepository<PressureSensorReading, Long> {
}
