package com.feetfit.server.repository;

import com.feetfit.server.domain.ShoeReview;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShoeReviewRepository extends JpaRepository<ShoeReview, Long> {
}
