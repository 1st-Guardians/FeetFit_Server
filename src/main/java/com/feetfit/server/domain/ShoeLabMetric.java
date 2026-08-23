package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import com.feetfit.server.domain.enums.ShoeLabCharacteristic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A raw RunRepeat metric. Canonical characteristic and source measurement
 * semantics are deliberately stored separately so different methods or units
 * are never collapsed into one number.
 */
@Entity
@Table(
        name = "shoe_lab_metric",
        indexes = @Index(
                name = "idx_shoe_lab_metric_comparison",
                columnList = "canonical_characteristic,unit,method_version,location,comparison_cohort"
        )
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ShoeLabMetric extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shoe_lab_metric_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shoe_lab_measurement_id", nullable = false)
    private ShoeLabMeasurement labMeasurement;

    @Enumerated(EnumType.STRING)
    @Column(name = "canonical_characteristic", nullable = false, length = 40)
    private ShoeLabCharacteristic canonicalCharacteristic;

    @Column(name = "source_metric_name", nullable = false)
    private String sourceMetricName;

    @Column(name = "metric_value", precision = 19, scale = 6)
    private BigDecimal value;

    @Column(name = "average_value", precision = 19, scale = 6)
    private BigDecimal averageValue;

    @Column(name = "source_min_value", precision = 19, scale = 6)
    private BigDecimal sourceMinValue;

    @Column(name = "source_max_value", precision = 19, scale = 6)
    private BigDecimal sourceMaxValue;

    @Column(length = 50)
    private String unit;

    @Column(name = "tested_size")
    private String testedSize;

    @Column(name = "method_name")
    private String methodName;

    @Column(name = "method_version", length = 100)
    private String methodVersion;

    @Column(length = 100)
    private String location;

    @Column(length = 100)
    private String variant;

    @Column(name = "comparison_sample_count")
    private Integer comparisonSampleCount;

    @Column(name = "comparison_cohort")
    private String comparisonCohort;

    @Column(name = "raw_value_text", columnDefinition = "TEXT")
    private String rawValueText;
}
