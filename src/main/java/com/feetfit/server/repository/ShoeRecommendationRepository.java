package com.feetfit.server.repository;

import com.feetfit.server.domain.ShoeRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShoeRecommendationRepository extends JpaRepository<ShoeRecommendation, Long> {
    boolean existsByUserId(Long userId);
    List<ShoeRecommendation> findByUserId(Long userId);
    List<ShoeRecommendation> findByUserIdAndShoeIdIn(Long userId, List<Long> shoeIds);
    Optional<ShoeRecommendation> findByUserIdAndShoeId(Long userId, Long shoeId);
}