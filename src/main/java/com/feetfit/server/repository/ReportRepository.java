package com.feetfit.server.repository;

import com.feetfit.server.domain.Report;
import com.feetfit.server.domain.enums.MeasurementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    @Query("""
            select r from Report r join fetch r.measurementSession session
            where r.user.id = :userId and session.status = :status and r.totalScore is not null
            order by session.measuredAt desc, session.id desc, r.id desc
            """)
    List<Report> findLatestCompleted(@Param("userId") Long userId, @Param("status") MeasurementStatus status,
                                     Pageable pageable);

    @Query("""
            select r.id from Report r join r.measurementSession session
            where session.status = :status and r.totalScore is not null
              and not exists (
                select newer.id from Report newer join newer.measurementSession newerSession
                where newer.user.id = r.user.id and newerSession.status = :status and newer.totalScore is not null
                  and (newerSession.measuredAt > session.measuredAt
                    or (newerSession.measuredAt = session.measuredAt and newerSession.id > session.id)
                    or (newerSession.id = session.id and newer.id > r.id)))
            order by r.id
            """)
    List<Long> findLatestCompletedReportIds(@Param("status") MeasurementStatus status);

    // 날짜 기준 가장 최근 리포트 조회
    Optional<Report> findTopByUserIdAndReportDateGreaterThanEqualAndReportDateLessThanOrderByReportDateDesc(
            Long userId,
            LocalDateTime startOfDay,
            LocalDateTime endOfDay);

    // 가장 최근 리포트 조회
    Optional<Report> findTopByUserIdOrderByReportDateDesc(Long userId);

    // 1년간 월별 평균 점수 조회
    @Query("SELECT r FROM Report r " +
            "WHERE r.user.id = :userId " +
            "AND r.reportDate >= :startDate " +
            "AND r.reportDate < :endDate " +
            "ORDER BY r.reportDate ASC")
    List<Report> findByUserIdAndReportDateBetween(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    Optional<Report> findTopByMeasurementSessionIdOrderByReportDateDescIdDesc(Long measurementSessionId);

    @Query("""
            SELECT COUNT(r) > 0 FROM Report r
            JOIN r.measurementSession session
            WHERE r.user.id = :userId
              AND r.totalScore IS NOT NULL
              AND session.status <> :excludedStatus
              AND (session.measuredAt > :measuredAt
                   OR (session.measuredAt = :measuredAt AND session.id > :measurementSessionId))
            """)
    boolean existsNewerAnalyzedMeasurement(
            @Param("userId") Long userId,
            @Param("measuredAt") LocalDateTime measuredAt,
            @Param("measurementSessionId") Long measurementSessionId,
            @Param("excludedStatus") MeasurementStatus excludedStatus);

    default Optional<Report> findByMeasurementSessionId(Long measurementSessionId) {
        return findTopByMeasurementSessionIdOrderByReportDateDescIdDesc(measurementSessionId);
    }
}
