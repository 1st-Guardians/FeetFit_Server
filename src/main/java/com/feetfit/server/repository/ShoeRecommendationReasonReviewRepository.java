package com.feetfit.server.repository;

import com.feetfit.server.domain.ShoeRecommendationReasonReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// TODO: 나중에 리포트 갱신될 때 기존 리뷰 연결 삭제하고 새로 저장하는 로직이 생기면 필요
public interface ShoeRecommendationReasonReviewRepository extends JpaRepository<ShoeRecommendationReasonReview, Long> {

    List<ShoeRecommendationReasonReview> findByReasonId(Long reasonId);

    void deleteByReasonId(Long reasonId);
}