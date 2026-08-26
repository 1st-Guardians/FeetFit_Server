package com.feetfit.server.repository;

import com.feetfit.server.domain.ShoeRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShoeRecommendationRepository extends JpaRepository<ShoeRecommendation, Long> {
    boolean existsByMeasurementSessionId(Long measurementSessionId);
    long countByMeasurementSessionId(Long measurementSessionId);
    List<ShoeRecommendation> findByMeasurementSessionIdAndShoeIdIn(
            Long measurementSessionId, List<Long> shoeIds);
    Optional<ShoeRecommendation> findByMeasurementSessionIdAndShoeId(
            Long measurementSessionId, Long shoeId);
}
