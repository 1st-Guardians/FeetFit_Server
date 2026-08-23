package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import com.feetfit.server.domain.enums.ShoeReviewSource;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "shoe_review",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_shoe_review_source_review_id",
                        columnNames = {"shoe_id", "source", "source_review_id"}
                ),
                @UniqueConstraint(
                        name = "uq_shoe_review_source_content_hash",
                        columnNames = {"shoe_id", "source", "content_hash"}
                )
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ShoeReview extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shoe_id", nullable = false)
    private Shoe shoe;

    @Column(nullable = false)
    private Float rating;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reviewText;

    @Column(name = "source_review_id")
    private String sourceReviewId;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    // 리뷰 수집 출처
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ShoeReviewSource source = ShoeReviewSource.MUSINSA;

    @Column(nullable = false)
    private LocalDateTime collectedAt;

    public void updateCrawledReview(
            Float rating,
            String reviewText,
            String sourceReviewId,
            String contentHash,
            LocalDateTime collectedAt) {
        this.rating = rating;
        this.reviewText = reviewText;
        this.sourceReviewId = sourceReviewId;
        this.contentHash = contentHash;
        this.collectedAt = collectedAt;
    }
}
