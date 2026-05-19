package com.feetfit.server.repository;

import com.feetfit.server.domain.MeasurementSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeasurementSessionRepository extends JpaRepository<MeasurementSession, Long> {
}
