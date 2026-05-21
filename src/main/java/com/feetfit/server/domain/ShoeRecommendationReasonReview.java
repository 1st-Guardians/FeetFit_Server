package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "shoe_recommendation_reason_review",
        uniqueConstraints = @UniqueConstraint(columnNames = {"reason_id", "review_id"}))  // ← 추가
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ShoeRecommendationReasonReview extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reason_id", nullable = false)
    private ShoeRecommendationReason reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private ShoeReview review;
}