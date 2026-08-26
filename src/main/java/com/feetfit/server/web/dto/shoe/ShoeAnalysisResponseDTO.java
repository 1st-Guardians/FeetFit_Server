package com.feetfit.server.web.dto.shoe;

import com.feetfit.server.domain.enums.FootRegion;
import com.feetfit.server.domain.enums.FootSide;
import com.feetfit.server.domain.enums.GaugeStatus;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.ReasonType;
import com.feetfit.server.domain.enums.RiskLevel;
import com.feetfit.server.domain.enums.ShoeLabCharacteristic;
import com.feetfit.server.domain.enums.ShoeReviewSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class ShoeAnalysisResponseDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendationContext {
        private Long measurementSessionId;
        private Long userId;
        private MeasurementStatus measurementStatus;
        private FootState footState;
        private List<ShoeAnalysisItem> shoes;
        private int currentPage;
        private int totalPages;
        private long totalElements;
        private boolean hasNext;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FootState {
        private DailyFootAnalysisItem dailyFootAnalysis;
        private TinaPedisAnalysisItem tinaPedisAnalysis;
        private HalluxValgusAnalysisItem halluxValgusAnalysis;
        private List<StaticPressureAnalysisItem> staticPressureAnalyses;
        private List<PressureSensorReadingItem> pressureSensorReadings;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyFootAnalysisItem {
        private Float balanceScore;
        private Float leftPressurePercent;
        private Float rightPressurePercent;
        private Float measuredLeftFootSizeMm;
        private Float measuredRightFootSizeMm;
        private Float leftFootWidthMm;
        private Float rightFootWidthMm;
        private Float avgTemperatureCelsius;
        private Float avgHumidityPercent;
        private String typeText;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TinaPedisAnalysisItem {
        private Integer fungalSuspicionSafetyScore;
        private Integer skinReactionSafetyScore;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HalluxValgusAnalysisItem {
        private Float leftToeAngleDegree;
        private Float rightToeAngleDegree;
        private Float riskScore;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StaticPressureAnalysisItem {
        private Long analysisId;
        private FootSide footSide;
        private Float leftPressureRatio;
        private Float rightPressureRatio;
        private Float forefootPressureRatio;
        private Float rearfootPressureRatio;
        private Float centerOfPressureX;
        private Float centerOfPressureY;
        private Float balanceScore;
        private GaugeStatus balanceStatus;
        private String analysisText;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PressureSensorReadingItem {
        private Long readingId;
        private FootSide footSide;
        private FootRegion footRegion;
        private Integer sensorIndex;
        private Float pressureValue;
        private Float pressureUnit;
        private LocalDateTime recordedAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShoeAnalysisItem {
        private Long shoeId;
        private String brandName;
        private String shoeName;
        private String modelCode;
        private String musinsaUrl;
        private Integer price;
        private String imageUrl;
        private Float overallRating;
        private Integer reviewCount;
        private List<ReviewItem> reviews;
        private List<LabMeasurementItem> labMeasurements;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewItem {
        private Long reviewId;
        private Float rating;
        private String reviewText;
        private ShoeReviewSource source;
        private LocalDateTime collectedAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LabMeasurementItem {
        private Long measurementId;
        private String source;
        private String sourceUrl;
        private String sourceBrandName;
        private String sourceShoeName;
        private String sourceModelCode;
        private String testedSize;
        private LocalDateTime capturedAt;
        private String parserVersion;
        private Float internalLengthMm;
        private Float widthMm;
        private Float toeboxWidthMm;
        private Float toeboxHeightMm;
        private Float insoleThicknessMm;
        private Float heelStackMm;
        private Float forefootStackMm;
        private List<RawMetricItem> rawMetrics;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RawMetricItem {
        private Long metricId;
        private ShoeLabCharacteristic canonicalCharacteristic;
        private String sourceMetricName;
        private BigDecimal value;
        private BigDecimal averageValue;
        private BigDecimal sourceMinValue;
        private BigDecimal sourceMaxValue;
        private String unit;
        private String testedSize;
        private String methodName;
        private String methodVersion;
        private String location;
        private String variant;
        private Integer comparisonSampleCount;
        private String comparisonCohort;
        private String rawValueText;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendationSummaryContext {
        private Long measurementSessionId;
        private Long userId;
        private Long shoeId;
        private String brandName;
        private String shoeName;
        private Float fitScore;
        private String pointSummary;
        private LocalDateTime analyzedAt;
        private List<SavedReasonItem> reasons;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SavedReasonItem {
        private ReasonType reasonType;
        private String title;
        private RiskLevel riskLevel;
        private String reviewSummary;
        private List<SavedReviewItem> reviews;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SavedReviewItem {
        private Long reviewId;
        private String reviewText;
        private ShoeReviewSource source;
    }
}
