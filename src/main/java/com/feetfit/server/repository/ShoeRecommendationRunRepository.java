package com.feetfit.server.repository;

import com.feetfit.server.domain.ShoeRecommendationRun;
import com.feetfit.server.domain.enums.ShoeRecommendationRunStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShoeRecommendationRunRepository extends JpaRepository<ShoeRecommendationRun, Long> {

    Optional<ShoeRecommendationRun> findByMeasurementSessionId(Long measurementSessionId);

    Optional<ShoeRecommendationRun> findByMeasurementSessionIdAndMeasurementSessionUserId(
            Long measurementSessionId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select run
            from ShoeRecommendationRun run
            where run.measurementSession.id = :measurementSessionId
            """)
    Optional<ShoeRecommendationRun> findByMeasurementSessionIdForUpdate(
            @Param("measurementSessionId") Long measurementSessionId);

    @Query("""
            select run
            from ShoeRecommendationRun run
            join fetch run.measurementSession session
            where session.user.id = :userId
              and run.status = :status
            order by session.measuredAt desc, session.id desc
            """)
    List<ShoeRecommendationRun> findLatestByUserIdAndStatus(
            @Param("userId") Long userId,
            @Param("status") ShoeRecommendationRunStatus status,
            Pageable pageable);
}
