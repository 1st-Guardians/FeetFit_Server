package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "measurement_session_id")
    private MeasurementSession measurementSession;

    @Column(nullable = false)
    private Float fitScore;

    @Column(columnDefinition = "TEXT")
    private String pointSummary;

    @Column(nullable = false)
    private LocalDateTime analyzedAt;

    @OneToMany(mappedBy = "shoeRecommendation", cascade = CascadeType.ALL)
    @Builder.Default
    private List<ShoeRecommendationReason> reasons = new ArrayList<>();

    public void updateShoeRecommendation(
            Float fitScore,
            String pointSummary,
            LocalDateTime analyzedAt,
            MeasurementSession measurementSession) {
        this.fitScore = fitScore;
        this.pointSummary = pointSummary;
        this.analyzedAt = analyzedAt;
        this.measurementSession = measurementSession;
    }

    public void updatePointSummary(String pointSummary) {
        this.pointSummary = pointSummary;
    }
}
