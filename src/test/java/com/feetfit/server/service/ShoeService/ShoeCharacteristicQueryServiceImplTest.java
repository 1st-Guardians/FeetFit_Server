package com.feetfit.server.service.ShoeService;

import com.feetfit.server.domain.Shoe;
import com.feetfit.server.domain.ShoeLabMeasurement;
import com.feetfit.server.domain.ShoeLabMetric;
import com.feetfit.server.domain.enums.ShoeCharacteristicLevel;
import com.feetfit.server.domain.enums.ShoeLabCharacteristic;
import com.feetfit.server.repository.ShoeLabMeasurementRepository;
import com.feetfit.server.repository.ShoeLabMetricRepository;
import com.feetfit.server.repository.ShoeRepository;
import com.feetfit.server.web.dto.shoe.ShoeCharacteristicResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoeCharacteristicQueryServiceImplTest {

    private static final Long TARGET_SHOE_ID = 45L;
    private static final String RUNREPEAT = "RUNREPEAT";
    private static final String COHORT = "road-running-shoes";

    @Mock ShoeRepository shoeRepository;
    @Mock ShoeLabMeasurementRepository measurementRepository;
    @Mock ShoeLabMetricRepository metricRepository;

    private ShoeCharacteristicQueryServiceImpl service;
    private long nextMetricId;

    @BeforeEach
    void setUp() {
        service = new ShoeCharacteristicQueryServiceImpl(
                shoeRepository,
                measurementRepository,
                metricRepository,
                new ShoeCharacteristicMetricSelector(),
                new CharacteristicLevelPolicy(),
                new ShoeCharacteristicSummaryService());
        nextMetricId = 1L;
    }

    @Test
    void returnsAllSevenCharacteristicsFromCompatibleLatestSnapshots() {
        ShoeLabMeasurement target = snapshot(101L, targetShoe(), "US 9");
        ShoeLabMeasurement lower = snapshot(102L, shoe(46L), "US 9");
        ShoeLabMeasurement upper = snapshot(103L, shoe(47L), "US 9");

        List<ShoeLabMetric> metrics = new ArrayList<>();
        metrics.addAll(allSevenMetrics(target, 0));
        metrics.addAll(allSevenMetrics(lower, 1));
        metrics.addAll(allSevenMetrics(upper, 2));
        stubSnapshots(List.of(target, lower, upper), metrics);

        ShoeCharacteristicResponseDTO.Result result =
                service.getCharacteristics(TARGET_SHOE_ID);

        assertThat(result.getShoeId()).isEqualTo(TARGET_SHOE_ID);
        assertThat(result.getCharacteristics())
                .extracting(ShoeCharacteristicResponseDTO.Item::getType)
                .containsExactly(ShoeLabCharacteristic.values());

        ShoeCharacteristicResponseDTO.Item cushion = item(result, ShoeLabCharacteristic.CUSHION);
        assertThat(cushion.getLevel()).isEqualTo(ShoeCharacteristicLevel.HIGH);
        assertThat(cushion.getValue()).isEqualByComparingTo("20");
        assertThat(cushion.getAverageValue()).isEqualByComparingTo("35.7");
        assertThat(cushion.getMinValue()).isEqualByComparingTo("15");
        assertThat(cushion.getMaxValue()).isEqualByComparingTo("55");
        assertThat(cushion.getUnit()).isEqualTo("AC");
        assertThat(cushion.getTestedSize()).isEqualTo("US 9");

        ShoeCharacteristicResponseDTO.Item width = item(result, ShoeLabCharacteristic.WIDTH_SPACE);
        assertThat(width.getAverageValue()).isEqualByComparingTo("95.2");
        assertThat(width.getMinValue()).isEqualByComparingTo("90");
        assertThat(width.getMaxValue()).isEqualByComparingTo("100");
        assertThat(width.getTestedSize()).isEqualTo("US 9");

        ShoeCharacteristicResponseDTO.Item heel = item(result, ShoeLabCharacteristic.HEEL_HOLD);
        assertThat(heel.getLevel()).isEqualTo(ShoeCharacteristicLevel.HIGH);
        assertThat(heel.getMinValue()).isEqualByComparingTo("1");
        assertThat(heel.getMaxValue()).isEqualByComparingTo("5");

        ShoeCharacteristicResponseDTO.Item breathability =
                item(result, ShoeLabCharacteristic.BREATHABILITY);
        assertThat(breathability.getUnit()).isEqualTo("BR");
        assertThat(breathability.getMinValue()).isEqualByComparingTo("100");
        assertThat(breathability.getMaxValue()).isEqualByComparingTo("140");

        // Metric-level testedSize is absent for shock absorption, so the
        // snapshot-level tested size is returned safely.
        assertThat(item(result, ShoeLabCharacteristic.SHOCK_ABSORPTION).getTestedSize())
                .isEqualTo("US 9");
        assertThat(result.getSummary())
                .contains("쿠션감", "앞코 공간", "뒤꿈치 구조", "통기성", "보통 수준");
    }

    @Test
    void returnsOnlyCharacteristicsActuallyPresentInAPartialSnapshot() {
        ShoeLabMeasurement target = snapshot(201L, targetShoe(), "US 9");
        ShoeLabMeasurement peerOne = snapshot(202L, shoe(46L), "US 9");
        ShoeLabMeasurement peerTwo = snapshot(203L, shoe(47L), "US 9");
        List<ShoeLabMetric> metrics = List.of(
                canonicalMetric(target, ShoeLabCharacteristic.WIDTH_SPACE, "93.4", "95.2", null, null),
                canonicalMetric(peerOne, ShoeLabCharacteristic.WIDTH_SPACE, "90", null, null, null),
                canonicalMetric(peerTwo, ShoeLabCharacteristic.WIDTH_SPACE, "100", null, null, null));
        stubSnapshots(List.of(target, peerOne, peerTwo), metrics);

        ShoeCharacteristicResponseDTO.Result result =
                service.getCharacteristics(TARGET_SHOE_ID);

        assertThat(result.getCharacteristics()).singleElement()
                .extracting(ShoeCharacteristicResponseDTO.Item::getType)
                .isEqualTo(ShoeLabCharacteristic.WIDTH_SPACE);
        assertThat(result.getSummary()).contains("발볼 여유");
    }

    @Test
    void returnsEmptyCharacteristicsAndNullSummaryWithoutRunRepeatSnapshot() {
        when(shoeRepository.existsById(TARGET_SHOE_ID)).thenReturn(true);
        when(measurementRepository.findLatestByShoeIdAndSource(TARGET_SHOE_ID, RUNREPEAT))
                .thenReturn(Optional.empty());

        ShoeCharacteristicResponseDTO.Result result =
                service.getCharacteristics(TARGET_SHOE_ID);

        assertThat(result.getShoeId()).isEqualTo(TARGET_SHOE_ID);
        assertThat(result.getCharacteristics()).isEmpty();
        assertThat(result.getSummary()).isNull();
        verify(metricRepository, never()).findByLabMeasurementIdOrderByIdAsc(any());
    }

    @Test
    void doesNotMixAcWithHaOrBrWithLegacyScoreForDistribution() {
        ShoeLabMeasurement target = snapshot(301L, targetShoe(), "US 9");
        ShoeLabMeasurement legacyOne = snapshot(302L, shoe(46L), "US 9");
        ShoeLabMeasurement legacyTwo = snapshot(303L, shoe(47L), "US 9");

        List<ShoeLabMetric> metrics = List.of(
                canonicalMetric(target, ShoeLabCharacteristic.CUSHION, "20", null, null, null),
                canonicalMetric(target, ShoeLabCharacteristic.BREATHABILITY, "120", null, null, null),
                canonicalMetricWithOverrides(
                        legacyOne, ShoeLabCharacteristic.CUSHION, "30", "HA", null, "primary"),
                canonicalMetricWithOverrides(
                        legacyTwo, ShoeLabCharacteristic.CUSHION, "40", "HA", null, "primary"),
                canonicalMetricWithOverrides(
                        legacyOne, ShoeLabCharacteristic.BREATHABILITY, "3", "score", null, null),
                canonicalMetricWithOverrides(
                        legacyTwo, ShoeLabCharacteristic.BREATHABILITY, "4", "score", null, null));
        stubSnapshots(List.of(target, legacyOne, legacyTwo), metrics);

        ShoeCharacteristicResponseDTO.Result result =
                service.getCharacteristics(TARGET_SHOE_ID);

        assertThat(result.getCharacteristics()).hasSize(2);
        for (ShoeCharacteristicResponseDTO.Item item : result.getCharacteristics()) {
            assertThat(item.getLevel()).isNull();
            assertThat(item.getMinValue()).isNull();
            assertThat(item.getMaxValue()).isNull();
        }
        assertThat(result.getSummary()).isNull();
    }

    @Test
    void evaluatesLegacyHaSoftnessWithinItsOwnCohortAndInvertsDirection() {
        ShoeLabMeasurement target = snapshot(401L, targetShoe(), "US 9");
        ShoeLabMeasurement peerOne = snapshot(402L, shoe(46L), "US 9");
        ShoeLabMeasurement peerTwo = snapshot(403L, shoe(47L), "US 9");
        List<ShoeLabMetric> metrics = List.of(
                canonicalMetricWithOverrides(
                        target, ShoeLabCharacteristic.CUSHION, "20", "HA", null, "primary"),
                canonicalMetricWithOverrides(
                        peerOne, ShoeLabCharacteristic.CUSHION, "30", "HA", null, "primary"),
                canonicalMetricWithOverrides(
                        peerTwo, ShoeLabCharacteristic.CUSHION, "40", "HA", null, "primary"));
        stubSnapshots(List.of(target, peerOne, peerTwo), metrics);

        ShoeCharacteristicResponseDTO.Item cushion = item(
                service.getCharacteristics(TARGET_SHOE_ID), ShoeLabCharacteristic.CUSHION);

        assertThat(cushion.getUnit()).isEqualTo("HA");
        assertThat(cushion.getLevel()).isEqualTo(ShoeCharacteristicLevel.HIGH);
        assertThat(cushion.getMinValue()).isEqualByComparingTo("20");
        assertThat(cushion.getMaxValue()).isEqualByComparingTo("40");
    }

    @Test
    void evaluatesLegacyBreathabilityScoreOnItsFixedOneToFiveScale() {
        ShoeLabMeasurement target = snapshot(501L, targetShoe(), "US 9");
        ShoeLabMeasurement peerOne = snapshot(502L, shoe(46L), "US 9");
        ShoeLabMeasurement peerTwo = snapshot(503L, shoe(47L), "US 9");
        List<ShoeLabMetric> metrics = List.of(
                canonicalMetricWithOverrides(
                        target, ShoeLabCharacteristic.BREATHABILITY, "3", "score", null, null),
                canonicalMetricWithOverrides(
                        peerOne, ShoeLabCharacteristic.BREATHABILITY, "1", "score", null, null),
                canonicalMetricWithOverrides(
                        peerTwo, ShoeLabCharacteristic.BREATHABILITY, "5", "score", null, null));
        stubSnapshots(List.of(target, peerOne, peerTwo), metrics);

        ShoeCharacteristicResponseDTO.Item breathability = item(
                service.getCharacteristics(TARGET_SHOE_ID),
                ShoeLabCharacteristic.BREATHABILITY);

        assertThat(breathability.getUnit()).isEqualTo("score");
        assertThat(breathability.getLevel()).isEqualTo(ShoeCharacteristicLevel.MEDIUM);
        assertThat(breathability.getMinValue()).isEqualByComparingTo("1");
        assertThat(breathability.getMaxValue()).isEqualByComparingTo("5");
    }

    @Test
    void doesNotUseForefootRowsToClassifyHeelShockOrEnergyReturn() {
        ShoeLabMeasurement target = snapshot(601L, targetShoe(), "US 9");
        ShoeLabMeasurement peerOne = snapshot(602L, shoe(46L), "US 9");
        ShoeLabMeasurement peerTwo = snapshot(603L, shoe(47L), "US 9");
        List<ShoeLabMetric> metrics = List.of(
                canonicalMetric(target, ShoeLabCharacteristic.SHOCK_ABSORPTION, "110", null, null, null),
                canonicalMetric(target, ShoeLabCharacteristic.ENERGY_RETURN, "70", null, null, null),
                canonicalMetricWithOverrides(
                        peerOne, ShoeLabCharacteristic.SHOCK_ABSORPTION, "100", "SA", "FOREFOOT", null),
                canonicalMetricWithOverrides(
                        peerTwo, ShoeLabCharacteristic.SHOCK_ABSORPTION, "120", "SA", "FOREFOOT", null),
                canonicalMetricWithOverrides(
                        peerOne, ShoeLabCharacteristic.ENERGY_RETURN, "60", "%", "FOREFOOT", null),
                canonicalMetricWithOverrides(
                        peerTwo, ShoeLabCharacteristic.ENERGY_RETURN, "80", "%", "FOREFOOT", null));
        stubSnapshots(List.of(target, peerOne, peerTwo), metrics);

        ShoeCharacteristicResponseDTO.Result result =
                service.getCharacteristics(TARGET_SHOE_ID);

        assertThat(result.getCharacteristics())
                .extracting(ShoeCharacteristicResponseDTO.Item::getType)
                .containsExactly(
                        ShoeLabCharacteristic.SHOCK_ABSORPTION,
                        ShoeLabCharacteristic.ENERGY_RETURN);
        assertThat(result.getCharacteristics())
                .allSatisfy(item -> {
                    assertThat(item.getLevel()).isNull();
                    assertThat(item.getMinValue()).isNull();
                    assertThat(item.getMaxValue()).isNull();
                });
        assertThat(result.getSummary()).isNull();
    }

    @Test
    void tiedCompatibleDistributionLeavesLevelAndObservedBoundsUndecided() {
        ShoeLabMeasurement target = snapshot(701L, targetShoe(), "US 9");
        ShoeLabMeasurement peerOne = snapshot(702L, shoe(46L), "US 9");
        ShoeLabMeasurement peerTwo = snapshot(703L, shoe(47L), "US 9");
        List<ShoeLabMetric> metrics = List.of(
                canonicalMetric(target, ShoeLabCharacteristic.WIDTH_SPACE, "93", "95.2", null, null),
                canonicalMetric(peerOne, ShoeLabCharacteristic.WIDTH_SPACE, "93", null, null, null),
                canonicalMetric(peerTwo, ShoeLabCharacteristic.WIDTH_SPACE, "93", null, null, null));
        stubSnapshots(List.of(target, peerOne, peerTwo), metrics);

        ShoeCharacteristicResponseDTO.Result result =
                service.getCharacteristics(TARGET_SHOE_ID);
        ShoeCharacteristicResponseDTO.Item width =
                item(result, ShoeLabCharacteristic.WIDTH_SPACE);

        assertThat(width.getLevel()).isNull();
        assertThat(width.getMinValue()).isNull();
        assertThat(width.getMaxValue()).isNull();
        assertThat(result.getSummary()).isNull();
    }

    @Test
    void unknownComparisonCohortSkipsDistributionQueryButKeepsRawFacts() {
        ShoeLabMeasurement target = snapshot(801L, targetShoe(), "US 9");
        ShoeLabMetric unknownCohort = canonicalMetricWithCohort(
                target,
                ShoeLabCharacteristic.WIDTH_SPACE,
                "93.4",
                "95.2",
                null);
        when(shoeRepository.existsById(TARGET_SHOE_ID)).thenReturn(true);
        when(measurementRepository.findLatestByShoeIdAndSource(TARGET_SHOE_ID, RUNREPEAT))
                .thenReturn(Optional.of(target));
        when(metricRepository.findByLabMeasurementIdOrderByIdAsc(target.getId()))
                .thenReturn(List.of(unknownCohort));

        ShoeCharacteristicResponseDTO.Result result =
                service.getCharacteristics(TARGET_SHOE_ID);
        ShoeCharacteristicResponseDTO.Item width =
                item(result, ShoeLabCharacteristic.WIDTH_SPACE);

        assertThat(width.getValue()).isEqualByComparingTo("93.4");
        assertThat(width.getAverageValue()).isEqualByComparingTo("95.2");
        assertThat(width.getLevel()).isNull();
        assertThat(width.getMinValue()).isNull();
        assertThat(width.getMaxValue()).isNull();
        assertThat(result.getSummary()).isNull();
        verify(metricRepository, never()).findLatestCompatibleMetrics(
                anyString(),
                any(ShoeLabCharacteristic.class),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString());
    }

    private void stubSnapshots(
            List<ShoeLabMeasurement> snapshots,
            List<ShoeLabMetric> metrics) {
        ShoeLabMeasurement target = snapshots.stream()
                .filter(snapshot -> snapshot.getShoe().getId().equals(TARGET_SHOE_ID))
                .findFirst()
                .orElseThrow();
        when(shoeRepository.existsById(TARGET_SHOE_ID)).thenReturn(true);
        when(measurementRepository.findLatestByShoeIdAndSource(TARGET_SHOE_ID, RUNREPEAT))
                .thenReturn(Optional.of(target));
        when(metricRepository.findByLabMeasurementIdOrderByIdAsc(target.getId()))
                .thenReturn(metrics.stream()
                        .filter(metric -> metric.getLabMeasurement().getId().equals(target.getId()))
                        .toList());
        when(metricRepository.findLatestCompatibleMetrics(
                anyString(),
                any(ShoeLabCharacteristic.class),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()))
                .thenAnswer(invocation -> {
                    ShoeLabCharacteristic characteristic = invocation.getArgument(1);
                    return metrics.stream()
                            .filter(metric -> metric.getCanonicalCharacteristic() == characteristic)
                            .toList();
                });
    }

    private List<ShoeLabMetric> allSevenMetrics(
            ShoeLabMeasurement snapshot,
            int sampleIndex) {
        String[][] values = {
                {"20", "30", "40"},
                {"110", "100", "120"},
                {"70", "60", "80"},
                {"93.4", "90", "100"},
                {"70", "65", "75"},
                {"5", "1", "3"},
                {"120", "100", "140"}
        };
        List<ShoeLabMetric> result = new ArrayList<>();
        for (int i = 0; i < ShoeLabCharacteristic.values().length; i++) {
            ShoeLabCharacteristic characteristic = ShoeLabCharacteristic.values()[i];
            String average = null;
            String sourceMin = null;
            String sourceMax = null;
            if (sampleIndex == 0 && characteristic == ShoeLabCharacteristic.CUSHION) {
                average = "35.7";
                sourceMin = "15";
                sourceMax = "55";
            } else if (sampleIndex == 0 && characteristic == ShoeLabCharacteristic.WIDTH_SPACE) {
                average = "95.2";
            }
            result.add(canonicalMetric(
                    snapshot,
                    characteristic,
                    values[i][sampleIndex],
                    average,
                    sourceMin,
                    sourceMax));
        }
        return result;
    }

    private ShoeLabMetric canonicalMetric(
            ShoeLabMeasurement snapshot,
            ShoeLabCharacteristic characteristic,
            String value,
            String averageValue,
            String sourceMinValue,
            String sourceMaxValue) {
        MetricShape shape = shape(characteristic);
        return metric(
                snapshot,
                characteristic,
                shape.sourceMetricName,
                value,
                averageValue,
                sourceMinValue,
                sourceMaxValue,
                shape.unit,
                shape.location,
                shape.variant,
                shape.testedSize);
    }

    private ShoeLabMetric canonicalMetricWithOverrides(
            ShoeLabMeasurement snapshot,
            ShoeLabCharacteristic characteristic,
            String value,
            String unit,
            String location,
            String variant) {
        MetricShape shape = shape(characteristic);
        return metric(
                snapshot,
                characteristic,
                shape.sourceMetricName,
                value,
                null,
                null,
                null,
                unit,
                location,
                variant,
                shape.testedSize);
    }

    private ShoeLabMetric canonicalMetricWithCohort(
            ShoeLabMeasurement snapshot,
            ShoeLabCharacteristic characteristic,
            String value,
            String averageValue,
            String comparisonCohort) {
        MetricShape shape = shape(characteristic);
        return metric(
                snapshot,
                characteristic,
                shape.sourceMetricName,
                value,
                averageValue,
                null,
                null,
                shape.unit,
                shape.location,
                shape.variant,
                shape.testedSize,
                comparisonCohort);
    }

    private ShoeLabMetric metric(
            ShoeLabMeasurement snapshot,
            ShoeLabCharacteristic characteristic,
            String sourceMetricName,
            String value,
            String averageValue,
            String sourceMinValue,
            String sourceMaxValue,
            String unit,
            String location,
            String variant,
            String testedSize) {
        return metric(
                snapshot,
                characteristic,
                sourceMetricName,
                value,
                averageValue,
                sourceMinValue,
                sourceMaxValue,
                unit,
                location,
                variant,
                testedSize,
                COHORT);
    }

    private ShoeLabMetric metric(
            ShoeLabMeasurement snapshot,
            ShoeLabCharacteristic characteristic,
            String sourceMetricName,
            String value,
            String averageValue,
            String sourceMinValue,
            String sourceMaxValue,
            String unit,
            String location,
            String variant,
            String testedSize,
            String comparisonCohort) {
        return ShoeLabMetric.builder()
                .id(nextMetricId++)
                .labMeasurement(snapshot)
                .canonicalCharacteristic(characteristic)
                .sourceMetricName(sourceMetricName)
                .value(decimal(value))
                .averageValue(decimal(averageValue))
                .sourceMinValue(decimal(sourceMinValue))
                .sourceMaxValue(decimal(sourceMaxValue))
                .unit(unit)
                .testedSize(testedSize)
                .methodName(characteristic.name() + " method")
                .methodVersion("v1")
                .location(location)
                .variant(variant)
                .comparisonCohort(comparisonCohort)
                .build();
    }

    private MetricShape shape(ShoeLabCharacteristic characteristic) {
        return switch (characteristic) {
            case CUSHION -> new MetricShape(
                    "Midsole softness", "AC", null, "primary", null);
            case SHOCK_ABSORPTION -> new MetricShape(
                    "Shock absorption heel", "SA", "HEEL", null, null);
            case ENERGY_RETURN -> new MetricShape(
                    "Energy return heel", "%", "HEEL", null, null);
            case WIDTH_SPACE -> new MetricShape(
                    "Width / Fit", "mm", null, "primary", "US 9");
            case TOEBOX_SPACE -> new MetricShape(
                    "Toebox width", "mm", null, "width", "US 9");
            case HEEL_HOLD -> new MetricShape(
                    "Heel counter stiffness", "score", "HEEL", null, null);
            case BREATHABILITY -> new MetricShape(
                    "Breathability", "BR", null, null, null);
        };
    }

    private ShoeCharacteristicResponseDTO.Item item(
            ShoeCharacteristicResponseDTO.Result result,
            ShoeLabCharacteristic type) {
        return result.getCharacteristics().stream()
                .filter(item -> item.getType() == type)
                .findFirst()
                .orElseThrow();
    }

    private ShoeLabMeasurement snapshot(Long id, Shoe shoe, String testedSize) {
        return ShoeLabMeasurement.builder()
                .id(id)
                .shoe(shoe)
                .source(RUNREPEAT)
                .testedSize(testedSize)
                .sourceUrl("https://runrepeat.com/example-" + id)
                .capturedAt(LocalDateTime.of(2026, 8, 1, 12, 0))
                .parserVersion("phase-b-v1")
                .build();
    }

    private Shoe targetShoe() {
        return shoe(TARGET_SHOE_ID);
    }

    private Shoe shoe(Long id) {
        return Shoe.builder()
                .id(id)
                .brandName("Brand")
                .shoeName("Shoe " + id)
                .modelCode("MODEL-" + id)
                .musinsaUrl("https://musinsa.example/" + id)
                .build();
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private record MetricShape(
            String sourceMetricName,
            String unit,
            String location,
            String variant,
            String testedSize) {
    }
}
