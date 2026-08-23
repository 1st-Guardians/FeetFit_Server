package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import com.feetfit.server.domain.enums.ReasonType;
import com.feetfit.server.domain.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "shoe_recommendation_reason")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ShoeRecommendationReason extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shoe_recommendation_id", nullable = false)
    private ShoeRecommendation shoeRecommendation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReasonType reasonType;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskLevel riskLevel;

    @Column(columnDefinition = "TEXT")
    private String reviewSummary;

    @OneToMany(mappedBy = "reason", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ShoeRecommendationReasonReview> reasonReviews = new ArrayList<>();

    public void updateReviewSummary(String reviewSummary) {
        this.reviewSummary = reviewSummary;
    }
}
