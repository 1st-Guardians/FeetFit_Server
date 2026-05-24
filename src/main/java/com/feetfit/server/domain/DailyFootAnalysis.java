package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import com.feetfit.server.domain.enums.GaugeStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "daily_foot_analysis")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DailyFootAnalysis extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "measurement_session_id", nullable = false)
    private MeasurementSession measurementSession;

    // 종합 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GaugeStatus conditionLevel;  // VERY_GOOD, ATTENTION_NEEDED, NEED_IMPROVEMENT

    // 상태 코멘트 목록
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "JSON")
    private List<String> conditionComments;

    // 압력 균형
    @Column(nullable = false)
    private Float balanceScore;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String balanceComment;

    @Column
    private Float leftPressurePercent;

    @Column
    private Float rightPressurePercent;

    @Column(columnDefinition = "TEXT")
    private String leftPressureImageUrl;

    @Column(columnDefinition = "TEXT")
    private String rightPressureImageUrl;

    // 발 사이즈
    @Column
    private Float measuredLeftFootSizeMm;

    @Column
    private Float measuredRightFootSizeMm;

    @Column
    private Float leftFootWidthMm;

    @Column
    private Float rightFootWidthMm;

    // 발 냄새
    @Column
    private Float footOdourPpm;

    @Column(columnDefinition = "TEXT")
    private String footOdourComment;

    // 환경
    @Column(nullable = false)
    private Float avgTemperatureCelsius;

    @Column(nullable = false)
    private Float avgHumidityPercent;

    // 관리 팁
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "JSON")
    private List<String> careTips;

    // 신발 리스트 페이지용 발 타입 텍스트
    @Column(nullable = false, columnDefinition = "TEXT")
    private String typeText;

    public void update(
            GaugeStatus conditionLevel,
            List<String> conditionComments,
            Float balanceScore,
            String balanceComment,
            Float leftPressurePercent,
            Float rightPressurePercent,
            String leftPressureImageUrl,
            String rightPressureImageUrl,
            Float measuredLeftFootSizeMm,
            Float measuredRightFootSizeMm,
            Float leftFootWidthMm,
            Float rightFootWidthMm,
            Float footOdourPpm,
            String footOdourComment,
            Float avgTemperatureCelsius,
            Float avgHumidityPercent,
            List<String> careTips,
            String typeText) {

        this.conditionLevel = conditionLevel;
        this.conditionComments = conditionComments;
        this.balanceScore = balanceScore;
        this.balanceComment = balanceComment;
        this.leftPressurePercent = leftPressurePercent;
        this.rightPressurePercent = rightPressurePercent;
        this.leftPressureImageUrl = leftPressureImageUrl;
        this.rightPressureImageUrl = rightPressureImageUrl;
        this.measuredLeftFootSizeMm = measuredLeftFootSizeMm;
        this.measuredRightFootSizeMm = measuredRightFootSizeMm;
        this.leftFootWidthMm = leftFootWidthMm;
        this.rightFootWidthMm = rightFootWidthMm;
        this.footOdourPpm = footOdourPpm;
        this.footOdourComment = footOdourComment;
        this.avgTemperatureCelsius = avgTemperatureCelsius;
        this.avgHumidityPercent = avgHumidityPercent;
        this.careTips = careTips;
        this.typeText = typeText;
    }
}