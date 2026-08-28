package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DailyFootAnalysis extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "measurement_id", nullable = false)
    private MeasurementSession measurementSession;

    // 자세 균형
    @Column
    private Float balanceScore;

    @Column(columnDefinition = "TEXT")
    private String balanceComment;

    // 압력 분포
    @Column
    private Float leftPressurePercent;

    @Column
    private Float rightPressurePercent;

    @Column(columnDefinition = "TEXT")
    private String leftPressureImageUrl;

    @Column(columnDefinition = "TEXT")
    private String rightPressureImageUrl;

    @Column(columnDefinition = "TEXT")
    private String leftPressureHeatmapImageUrl;

    @Column(columnDefinition = "TEXT")
    private String rightPressureHeatmapImageUrl;

    @Column(columnDefinition = "TEXT")
    private String leftPlantarFootprintImageUrl;

    @Column(columnDefinition = "TEXT")
    private String rightPlantarFootprintImageUrl;

    @Column(columnDefinition = "TEXT")
    private String plantarFootprintAnalysisText;

    // 발 수치
    @Column
    private Float measuredLeftFootSizeMm;

    @Column
    private Float measuredRightFootSizeMm;

    @Column
    private Float leftFootWidthMm;

    @Column
    private Float rightFootWidthMm;

    // 발 냄새 - VOC 센서 원시값
    @Column
    private Integer tvocPpb;

    @Column
    private Integer baselineTvocPpb;

    // 보정 후 rawPpm (clamp 이전 값 — DB 보존용)
    @Column
    private Float rawFootOdourPpm;

    // 프론트 표시용 (0~5ppm clamp 적용)
    @Column
    private Float footOdourPpm;

    @Column(columnDefinition = "TEXT")
    private String footOdourComment;

    // 환경
    @Column
    private Float beforeTemperatureCelsius;

    @Column
    private Float beforeHumidityPercent;

    @Column
    private Float afterTemperatureCelsius;

    @Column
    private Float afterHumidityPercent;

    @Column
    private Float avgTemperatureCelsius;

    @Column
    private Float avgHumidityPercent;

    // 관리 팁
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    private List<String> careTips;

    // 신발 리스트 페이지용 발 타입 텍스트
    @Column(columnDefinition = "TEXT")
    private String typeText;

    public void updateBalance(Float balanceScore, String balanceComment) {
        this.balanceScore = balanceScore;
        this.balanceComment = balanceComment;
    }

    public void updatePressure(Float leftPressurePercent, Float rightPressurePercent,
                               String leftPressureImageUrl, String rightPressureImageUrl) {
        this.leftPressurePercent = leftPressurePercent;
        this.rightPressurePercent = rightPressurePercent;
        this.leftPressureImageUrl = leftPressureImageUrl;
        this.rightPressureImageUrl = rightPressureImageUrl;
    }

    public void updatePressureHeatmapImages(String leftPressureHeatmapImageUrl, String rightPressureHeatmapImageUrl) {
        this.leftPressureHeatmapImageUrl = leftPressureHeatmapImageUrl;
        this.rightPressureHeatmapImageUrl = rightPressureHeatmapImageUrl;
    }

    public void updatePlantarFootprint(String leftPlantarFootprintImageUrl,
                                       String rightPlantarFootprintImageUrl,
                                       String plantarFootprintAnalysisText) {
        this.leftPlantarFootprintImageUrl = leftPlantarFootprintImageUrl;
        this.rightPlantarFootprintImageUrl = rightPlantarFootprintImageUrl;
        this.plantarFootprintAnalysisText = plantarFootprintAnalysisText;
    }

    public void updateMetrics(Float measuredLeftFootSizeMm, Float measuredRightFootSizeMm,
                              Float leftFootWidthMm, Float rightFootWidthMm) {
        this.measuredLeftFootSizeMm = measuredLeftFootSizeMm;
        this.measuredRightFootSizeMm = measuredRightFootSizeMm;
        this.leftFootWidthMm = leftFootWidthMm;
        this.rightFootWidthMm = rightFootWidthMm;
    }

    public void updateOdour(Integer tvocPpb, Integer baselineTvocPpb,
                            Float rawFootOdourPpm, Float footOdourPpm, String footOdourComment) {
        this.tvocPpb = tvocPpb;
        this.baselineTvocPpb = baselineTvocPpb;
        this.rawFootOdourPpm = rawFootOdourPpm;
        this.footOdourPpm = footOdourPpm;
        this.footOdourComment = footOdourComment;
    }

    public void updateEnvironment(Float beforeTemperatureCelsius, Float beforeHumidityPercent,
                                  Float afterTemperatureCelsius, Float afterHumidityPercent,
                                  Float avgTemperatureCelsius, Float avgHumidityPercent) {
        if (beforeTemperatureCelsius != null) {
            this.beforeTemperatureCelsius = beforeTemperatureCelsius;
        }
        if (beforeHumidityPercent != null) {
            this.beforeHumidityPercent = beforeHumidityPercent;
        }
        if (afterTemperatureCelsius != null) {
            this.afterTemperatureCelsius = afterTemperatureCelsius;
        }
        if (afterHumidityPercent != null) {
            this.afterHumidityPercent = afterHumidityPercent;
        }
        this.avgTemperatureCelsius = avgTemperatureCelsius;
        this.avgHumidityPercent = avgHumidityPercent;
    }

    public void updateTypeText(String typeText) {
        this.typeText = typeText;
    }

    public void updateCareTips(List<String> careTips) {
        this.careTips = careTips;
    }
}
