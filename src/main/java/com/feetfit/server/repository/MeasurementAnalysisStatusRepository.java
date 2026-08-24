package com.feetfit.server.repository;

import com.feetfit.server.domain.MeasurementAnalysisStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MeasurementAnalysisStatusRepository extends JpaRepository<MeasurementAnalysisStatus, Long> {

    Optional<MeasurementAnalysisStatus> findByMeasurementSessionId(Long measurementSessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select status
            from MeasurementAnalysisStatus status
            where status.measurementSession.id = :measurementSessionId
            """)
    Optional<MeasurementAnalysisStatus> findByMeasurementSessionIdForUpdate(
            @Param("measurementSessionId") Long measurementSessionId);
}
