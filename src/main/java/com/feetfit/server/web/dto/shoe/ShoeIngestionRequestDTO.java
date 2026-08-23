package com.feetfit.server.web.dto.shoe;

import com.feetfit.server.domain.enums.ShoeImportSource;
import com.feetfit.server.domain.enums.ShoeLabCharacteristic;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ShoeIngestionRequestDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MusinsaImportRequest {
        @NotNull
        private ShoeImportSource source;

        @NotNull
        private LocalDateTime collectedAt;

        @NotEmpty
        @Valid
        @Builder.Default
        private List<MusinsaShoeItem> shoes = new ArrayList<>();
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MusinsaShoeItem {
        @NotBlank
        private String goodsNo;

        @NotBlank
        private String brandName;

        @NotBlank
        private String shoeName;

        @NotBlank
        private String modelCode;

        @NotBlank
        private String musinsaUrl;

        /**
         * Optional source slug (for example, "newbalance") used by the crawler
         * when resolving an allowed RunRepeat brand catalog. It is provenance,
         * not a trusted matching key and is not persisted as Shoe.brandName.
         */
        private String sourceBrandKey;

        @PositiveOrZero
        private Integer price;

        private String imageUrl;

        @DecimalMin("0.0")
        @DecimalMax("5.0")
        private Float overallRating;

        @NotNull
        @PositiveOrZero
        private Integer reviewCount;

        @Valid
        @NotNull
        @Builder.Default
        private List<MusinsaReviewItem> reviews = new ArrayList<>();
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MusinsaReviewItem {
        private String sourceReviewId;

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("5.0")
        private Float rating;

        @NotBlank
        private String reviewText;

        @NotNull
        private LocalDateTime collectedAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RunRepeatImportRequest {
        @NotNull
        private ShoeImportSource source;

        @NotEmpty
        @Valid
        @Builder.Default
        private List<RunRepeatSnapshotItem> items = new ArrayList<>();
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RunRepeatSnapshotItem {
        private String externalKey;

        @NotBlank
        private String brandName;

        @NotBlank
        private String shoeName;

        private String modelCode;

        @NotBlank
        private String sourceUrl;

        private String testedSize;

        private Float internalLengthMm;
        private Float widthMm;
        private Float toeboxWidthMm;
        private Float toeboxHeightMm;
        private Float insoleThicknessMm;
        private Float heelStackMm;
        private Float forefootStackMm;

        @NotNull
        private LocalDateTime capturedAt;

        @NotBlank
        private String parserVersion;

        @NotNull
        @Valid
        @Builder.Default
        private List<RawMetricItem> rawMetrics = new ArrayList<>();
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RawMetricItem {
        @NotNull
        private ShoeLabCharacteristic canonicalCharacteristic;

        @NotBlank
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

        @PositiveOrZero
        private Integer comparisonSampleCount;

        private String comparisonCohort;
        private String rawValueText;
    }
}
