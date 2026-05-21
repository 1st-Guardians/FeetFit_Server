package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import com.feetfit.server.domain.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "shoe_recommendation")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ShoeRecommendation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shoe_id", nullable = false)
    private Shoe shoe;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskLevel riskLevel;

    @Column(nullable = false)
    private Float fitScore;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String pointSummary;

    @Column(nullable = false)
    private LocalDateTime analyzedAt;

    @OneToMany(mappedBy = "shoeRecommendation", cascade = CascadeType.ALL)
    @Builder.Default
    private List<ShoeRecommendationReason> reasons = new ArrayList<>();

    @OneToMany(mappedBy = "shoeRecommendation", cascade = CascadeType.ALL)
    @Builder.Default
    private List<ShoeReview> reviews = new ArrayList<>();
}
