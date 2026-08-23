package com.feetfit.server.repository;

import com.feetfit.server.domain.ShoeRecommendationReason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

import com.feetfit.server.domain.enums.ReasonType;

public interface ShoeRecommendationReasonRepository extends JpaRepository<ShoeRecommendationReason, Long> {
    List<ShoeRecommendationReason> findByShoeRecommendationId(Long shoeRecommendationId);

    Optional<ShoeRecommendationReason> findByShoeRecommendationIdAndReasonType(
            Long shoeRecommendationId, ReasonType reasonType);
}
