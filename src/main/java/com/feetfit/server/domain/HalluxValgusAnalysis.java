package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import com.feetfit.server.domain.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "hallux_valgus_analysis")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class HalluxValgusAnalysis extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "measurement_session_id", nullable = false)
    private MeasurementSession measurementSession;

    // 양 발 이미지
    @Column
    private String imageUrl;

    // 왼발
    @Column
    private Float leftToeAngleDegree;

    @Column
    private String leftAnalysisText;

    // 오른발
    @Column
    private Float rightToeAngleDegree;

    @Column
    private String rightAnalysisText;

    // 종합
    @Column(nullable = false)
    private Float riskScore;

    @Column(columnDefinition = "TEXT")
    private String scoreAnalysisText;

    public void updateHalluxValgusAnalysis(
            String imageUrl,
            Float leftToeAngleDegree, String leftAnalysisText,
            Float rightToeAngleDegree, String rightAnalysisText,
            Float riskScore, String scoreAnalysisText) {

        this.imageUrl = imageUrl;
        this.leftToeAngleDegree = leftToeAngleDegree;
        this.leftAnalysisText = leftAnalysisText;
        this.rightToeAngleDegree = rightToeAngleDegree;
        this.rightAnalysisText = rightAnalysisText;
        this.riskScore = riskScore;
        this.scoreAnalysisText = scoreAnalysisText;
    }
}