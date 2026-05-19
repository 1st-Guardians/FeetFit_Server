package com.feetfit.server.repository;

import com.feetfit.server.domain.ShoeRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShoeRecommendationRepository extends JpaRepository<ShoeRecommendation, Long> {
}
