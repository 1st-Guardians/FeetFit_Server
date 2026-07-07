package com.feetfit.server.repository;

import com.feetfit.server.domain.ShoeRecommendationReason;
import com.feetfit.server.domain.enums.ReasonType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShoeRecommendationReasonRepository extends JpaRepository<ShoeRecommendationReason, Long> {
    List<ShoeRecommendationReason> findByShoeRecommendationId(Long shoeRecommendationId);
    Optional<ShoeRecommendationReason> findByShoeRecommendationIdAndReasonType(Long shoeRecommendationId, ReasonType reasonType);
}