package com.feetfit.server.repository;

import com.feetfit.server.domain.ShoeReview;
import org.springframework.data.jpa.repository.JpaRepository;

import com.feetfit.server.domain.enums.ShoeReviewSource;

import java.util.List;
import java.util.Optional;

public interface ShoeReviewRepository extends JpaRepository<ShoeReview, Long> {
    Optional<ShoeReview> findByShoeIdAndSourceAndSourceReviewId(
            Long shoeId, ShoeReviewSource source, String sourceReviewId);

    Optional<ShoeReview> findByShoeIdAndSourceAndContentHash(
            Long shoeId, ShoeReviewSource source, String contentHash);

    List<ShoeReview> findByShoeIdAndSourceAndSourceReviewIdIsNullAndContentHashIsNullAndReviewTextAndRating(
            Long shoeId, ShoeReviewSource source, String reviewText, Float rating);

    List<ShoeReview> findByShoeIdOrderByIdAsc(Long shoeId);

    List<ShoeReview> findByShoeIdAndSourceOrderByIdAsc(Long shoeId, ShoeReviewSource source);

    List<ShoeReview> findByShoeIdInAndSourceOrderByIdAsc(
            List<Long> shoeIds, ShoeReviewSource source);
}
