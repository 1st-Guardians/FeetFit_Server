package com.feetfit.server.repository;

import com.feetfit.server.domain.ShoeRecommendationReasonReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShoeRecommendationReasonReviewRepository extends JpaRepository<ShoeRecommendationReasonReview, Long> {

    List<ShoeRecommendationReasonReview> findByReasonId(Long reasonId);

    void deleteByReasonId(Long reasonId);

    // 발 적합도 갱신 시 기존 reason들의 연결을 한 번의 DELETE로 정리 (건별 cascade 삭제 대신 사용)
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ShoeRecommendationReasonReview rr WHERE rr.reason.id IN :reasonIds")
    void deleteByReasonIdIn(@Param("reasonIds") List<Long> reasonIds);
}