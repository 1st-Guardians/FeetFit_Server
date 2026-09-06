package com.feetfit.server.repository;

import com.feetfit.server.domain.ShoeRecommendationReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

import com.feetfit.server.domain.enums.ReasonType;

public interface ShoeRecommendationReasonRepository extends JpaRepository<ShoeRecommendationReason, Long> {
    List<ShoeRecommendationReason> findByShoeRecommendationId(Long shoeRecommendationId);

    @EntityGraph(attributePaths = {"reasonReviews", "reasonReviews.review"})
    @Query("""
            SELECT DISTINCT reason
            FROM ShoeRecommendationReason reason
            WHERE reason.shoeRecommendation.id = :shoeRecommendationId
            """)
    List<ShoeRecommendationReason> findDetailByShoeRecommendationId(
            @Param("shoeRecommendationId") Long shoeRecommendationId);

    Optional<ShoeRecommendationReason> findByShoeRecommendationIdAndReasonType(
            Long shoeRecommendationId, ReasonType reasonType);

    @Query(value = """
            SELECT COUNT(*)
            FROM shoe_recommendation recommendation
            LEFT JOIN (
                SELECT shoe_recommendation_id,
                       COUNT(*) AS reason_count,
                       COUNT(DISTINCT reason_type) AS distinct_reason_count,
                       SUM(CASE
                               WHEN reason_type IN ('FOREFOOT', 'HEEL', 'INSOLE') THEN 1
                               ELSE 0
                           END) AS valid_reason_count
                FROM shoe_recommendation_reason
                GROUP BY shoe_recommendation_id
            ) reason_counts
              ON reason_counts.shoe_recommendation_id = recommendation.id
            WHERE recommendation.measurement_session_id = :measurementSessionId
              AND (
                  COALESCE(reason_counts.reason_count, 0) <> 3
                  OR COALESCE(reason_counts.distinct_reason_count, 0) <> 3
                  OR COALESCE(reason_counts.valid_reason_count, 0) <> 3
              )
            """, nativeQuery = true)
    long countIncompleteReasonSetsByMeasurementSessionId(
            @Param("measurementSessionId") Long measurementSessionId);
}
