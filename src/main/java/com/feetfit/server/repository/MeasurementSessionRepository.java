package com.feetfit.server.repository;

import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.MetricType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface MeasurementSessionRepository extends JpaRepository<MeasurementSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select measurementSession from MeasurementSession measurementSession where measurementSession.id = :id")
    Optional<MeasurementSession> findByIdForUpdate(@Param("id") Long id);

    @Query("select session.id from MeasurementSession session where session.user.id = :userId order by session.id")
    List<Long> findIdsByUserId(@Param("userId") Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    interface PressureTimeoutCandidate {
        Long getId();
        int getPressureRetryCount();
        MeasurementStatus getStatus();
    }

    @Query("""
            select session.id as id, session.pressureRetryCount as pressureRetryCount, session.status as status
            from MeasurementSession session
            where session.status in :statuses
              and coalesce(session.pressureProgressAt, session.updatedAt, session.createdAt, session.measuredAt) <= :staleBefore
              and not exists (
                select flags.id from MeasurementAnalysisStatus flags
                where flags.measurementSession = session
                  and flags.pressureCaptureCompleted = true
                  and flags.pressureAnalysisCompleted = true
                  and exists (
                    select metric.id from MetricAnalysisResult metric
                    where metric.report.measurementSession = session and metric.metricType = :pressureMetric
                  )
              )
            order by coalesce(session.pressureProgressAt, session.updatedAt, session.createdAt, session.measuredAt), session.id
            """)
    List<PressureTimeoutCandidate> findPressureTimeoutCandidates(
            @Param("statuses") Set<MeasurementStatus> statuses,
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("pressureMetric") MetricType pressureMetric,
            Pageable pageable);

    @Query("""
            select count(flags) > 0 from MeasurementAnalysisStatus flags
            where flags.measurementSession.id = :sessionId
              and flags.pressureCaptureCompleted = true
              and flags.pressureAnalysisCompleted = true
              and exists (
                select metric.id from MetricAnalysisResult metric
                where metric.report.measurementSession.id = :sessionId and metric.metricType = :pressureMetric
              )
            """)
    boolean hasCompletedPressurePipeline(@Param("sessionId") Long sessionId,
                                         @Param("pressureMetric") MetricType pressureMetric);

    boolean existsByUserIdAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThan(
            Long userId,
            LocalDateTime startOfDay,
            LocalDateTime startOfNextDay
    );

    boolean existsByUserIdAndStatusAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThan(
            Long userId,
            MeasurementStatus status,
            LocalDateTime startOfDay,
            LocalDateTime startOfNextDay
    );

    List<MeasurementSession> findByUserIdAndStatusAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThan(
            Long userId,
            MeasurementStatus status,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    );

    // Repository - String으로 반환
    @Query(value = "SELECT DISTINCT DATE_FORMAT(ms.measured_at, '%Y-%m-%d') " +
            "FROM measurement_session ms " +
            "WHERE ms.user_id = :userId " +
            "AND YEAR(ms.measured_at) = :year " +
            "AND MONTH(ms.measured_at) = :month " +
            "AND ms.status = 'COMPLETED' " +
            "ORDER BY DATE_FORMAT(ms.measured_at, '%Y-%m-%d') ASC",
            nativeQuery = true)
    List<String> findMeasuredDatesByYearAndMonth(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("month") int month
    );
}
