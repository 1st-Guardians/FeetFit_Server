package com.feetfit.server.repository;

import com.feetfit.server.domain.ShoeRecommendationReason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShoeRecommendationReasonRepository extends JpaRepository<ShoeRecommendationReason, Long> {
    List<ShoeRecommendationReason> findByShoeRecommendationId(Long shoeRecommendationId);
}