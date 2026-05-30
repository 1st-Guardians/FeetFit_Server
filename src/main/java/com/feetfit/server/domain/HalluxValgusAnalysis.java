package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
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

    // 왼발
    @Column
    private Float leftToeAngleDegree;

    @Column(columnDefinition = "TEXT")
    private String leftAnalysisText;

    @Column(columnDefinition = "TEXT")
    private String leftImageUrl;  // ← 왼발 이미지 URL

    // 오른발
    @Column
    private Float rightToeAngleDegree;

    @Column(columnDefinition = "TEXT")
    private String rightAnalysisText;

    @Column(columnDefinition = "TEXT")
    private String rightImageUrl;  // ← 오른발 이미지 URL

    // 종합
    @Column(nullable = false)
    private Float riskScore;

    @Column(columnDefinition = "TEXT")
    private String scoreAnalysisText;

    public void updateHalluxValgusAnalysis(
            Float leftToeAngleDegree, String leftAnalysisText, String leftImageUrl,
            Float rightToeAngleDegree, String rightAnalysisText, String rightImageUrl,
            Float riskScore, String scoreAnalysisText) {

        this.leftToeAngleDegree = leftToeAngleDegree;
        this.leftAnalysisText = leftAnalysisText;
        this.leftImageUrl = leftImageUrl;
        this.rightToeAngleDegree = rightToeAngleDegree;
        this.rightAnalysisText = rightAnalysisText;
        this.rightImageUrl = rightImageUrl;
        this.riskScore = riskScore;
        this.scoreAnalysisText = scoreAnalysisText;
    }
}