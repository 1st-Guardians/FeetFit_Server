package com.feetfit.server.service.ShoeService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.ShoeHandler;
import com.feetfit.server.domain.ShoeLabMeasurement;
import com.feetfit.server.domain.ShoeLabMetric;
import com.feetfit.server.domain.enums.MetricDirection;
import com.feetfit.server.domain.enums.ShoeCharacteristicLevel;
import com.feetfit.server.domain.enums.ShoeLabCharacteristic;
import com.feetfit.server.repository.ShoeLabMeasurementRepository;
import com.feetfit.server.repository.ShoeLabMetricRepository;
import com.feetfit.server.repository.ShoeRepository;
import com.feetfit.server.web.dto.shoe.ShoeCharacteristicResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds screen-neutral characteristics from the latest RunRepeat snapshot.
 * Historical snapshots are never merged into the selected shoe. They are also
 * not over-counted in a comparison distribution: at most one compatible raw
 * metric from each shoe's latest snapshot is used.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShoeCharacteristicQueryServiceImpl implements ShoeCharacteristicQueryService {

    private static final String RUNREPEAT = "RUNREPEAT";
    private static final BigDecimal FIXED_SCALE_MIN = BigDecimal.ONE;
    private static final BigDecimal FIXED_SCALE_MAX = BigDecimal.valueOf(5L);

    private final ShoeRepository shoeRepository;
    private final ShoeLabMeasurementRepository measurementRepository;
    private final ShoeLabMetricRepository metricRepository;
    private final ShoeCharacteristicMetricSelector metricSelector;
    private final CharacteristicLevelPolicy levelPolicy;
    private final ShoeCharacteristicSummaryService summaryService;

    @Override
    public ShoeCharacteristicResponseDTO.Result getCharacteristics(Long shoeId) {
        if (!shoeRepository.existsById(shoeId)) {
            throw new ShoeHandler(ErrorStatus.SHOE_NOT_FOUND);
        }

        Optional<ShoeLabMeasurement> targetSnapshot =
                measurementRepository.findLatestByShoeIdAndSource(shoeId, RUNREPEAT);
        if (targetSnapshot.isEmpty()) {
            return emptyResult(shoeId);
        }

        ShoeLabMeasurement target = targetSnapshot.get();
        List<ShoeLabMetric> targetMetrics =
                metricRepository.findByLabMeasurementIdOrderByIdAsc(target.getId());
        List<ShoeCharacteristicResponseDTO.Item> items = new ArrayList<>();
        Map<ShoeLabCharacteristic, ShoeCharacteristicLevel> levels =
                new EnumMap<>(ShoeLabCharacteristic.class);

        for (ShoeLabCharacteristic characteristic : ShoeLabCharacteristic.values()) {
            Optional<ShoeLabMetric> selected = metricSelector.select(characteristic, targetMetrics);
            if (selected.isEmpty()) {
                continue;
            }

            ShoeLabMetric metric = selected.get();
            List<BigDecimal> distribution = compatibleDistribution(metric);
            ShoeCharacteristicLevel level = levelPolicy.determineLevel(
                    metric.getValue(), distribution, direction(characteristic));
            levels.put(characteristic, level);

            Bounds bounds = bounds(metric, distribution);
            items.add(ShoeCharacteristicResponseDTO.Item.builder()
                    .type(characteristic)
                    .level(level)
                    .value(metric.getValue())
                    .averageValue(metric.getAverageValue())
                    .minValue(bounds.minimum())
                    .maxValue(bounds.maximum())
                    .unit(metric.getUnit())
                    .testedSize(firstNonBlank(metric.getTestedSize(), target.getTestedSize()))
                    .build());
        }

        return ShoeCharacteristicResponseDTO.Result.builder()
                .shoeId(shoeId)
                .summary(summaryService.summarize(levels))
                .characteristics(items)
                .build();
    }

    private ShoeCharacteristicResponseDTO.Result emptyResult(Long shoeId) {
        return ShoeCharacteristicResponseDTO.Result.builder()
                .shoeId(shoeId)
                .summary(null)
                .characteristics(List.of())
                .build();
    }

    private List<BigDecimal> compatibleDistribution(ShoeLabMetric target) {
        // An absent cohort has unknown category semantics, so it must not be
        // silently combined with other uncategorised source pages.
        if (normalize(target.getComparisonCohort()) == null) {
            return List.of();
        }

        String testedSize = requiresEqualTestedSize(target.getCanonicalCharacteristic())
                ? normalize(effectiveTestedSize(target))
                : "";
        List<ShoeLabMetric> cohortRows = metricRepository.findLatestCompatibleMetrics(
                RUNREPEAT,
                target.getCanonicalCharacteristic(),
                normalizedOrEmpty(target.getUnit()),
                normalizedOrEmpty(target.getMethodName()),
                normalizedOrEmpty(target.getMethodVersion()),
                normalizedOrEmpty(target.getLocation()),
                normalizedOrEmpty(target.getVariant()),
                normalize(target.getComparisonCohort()),
                testedSize == null ? "" : testedSize);

        Map<Long, List<ShoeLabMetric>> rowsBySnapshot = cohortRows.stream()
                .filter(metric -> metric.getValue() != null)
                .filter(metric -> compatible(target, metric))
                .collect(java.util.stream.Collectors.groupingBy(
                        metric -> metric.getLabMeasurement().getId()));

        List<BigDecimal> values = new ArrayList<>();
        for (List<ShoeLabMetric> matches : rowsBySnapshot.values()) {
            // Duplicate compatible rows in one snapshot are ambiguous. That
            // shoe contributes no sample instead of being arbitrarily weighted.
            if (matches.size() == 1) {
                values.add(matches.get(0).getValue());
            }
        }
        return values;
    }

    private boolean compatible(ShoeLabMetric target, ShoeLabMetric candidate) {
        if (target.getCanonicalCharacteristic() != candidate.getCanonicalCharacteristic()) {
            return false;
        }
        if (!same(target.getUnit(), candidate.getUnit())
                || !same(target.getMethodName(), candidate.getMethodName())
                || !same(target.getMethodVersion(), candidate.getMethodVersion())
                || !same(target.getLocation(), candidate.getLocation())
                || !same(target.getVariant(), candidate.getVariant())
                || !same(target.getComparisonCohort(), candidate.getComparisonCohort())
                || !sameSourceMetric(target.getSourceMetricName(), candidate.getSourceMetricName())) {
            return false;
        }

        if (requiresEqualTestedSize(target.getCanonicalCharacteristic())) {
            return same(effectiveTestedSize(target), effectiveTestedSize(candidate));
        }
        return true;
    }

    private boolean requiresEqualTestedSize(ShoeLabCharacteristic characteristic) {
        return characteristic == ShoeLabCharacteristic.WIDTH_SPACE
                || characteristic == ShoeLabCharacteristic.TOEBOX_SPACE;
    }

    private String effectiveTestedSize(ShoeLabMetric metric) {
        return firstNonBlank(
                metric.getTestedSize(),
                metric.getLabMeasurement().getTestedSize());
    }

    private Bounds bounds(ShoeLabMetric metric, List<BigDecimal> distribution) {
        BigDecimal fixedMinimum = fixedScale(metric) ? FIXED_SCALE_MIN : null;
        BigDecimal fixedMaximum = fixedScale(metric) ? FIXED_SCALE_MAX : null;

        BigDecimal observedMinimum = null;
        BigDecimal observedMaximum = null;
        if (levelPolicy.hasSufficientCohort(distribution)) {
            observedMinimum = distribution.stream().min(BigDecimal::compareTo).orElse(null);
            observedMaximum = distribution.stream().max(BigDecimal::compareTo).orElse(null);
            if (observedMinimum != null && observedMinimum.compareTo(observedMaximum) == 0) {
                observedMinimum = null;
                observedMaximum = null;
            }
        }

        return new Bounds(
                firstNonNull(metric.getSourceMinValue(), fixedMinimum, observedMinimum),
                firstNonNull(metric.getSourceMaxValue(), fixedMaximum, observedMaximum));
    }

    private boolean fixedScale(ShoeLabMetric metric) {
        if (!same(metric.getUnit(), "score")) {
            return false;
        }
        return metric.getCanonicalCharacteristic() == ShoeLabCharacteristic.HEEL_HOLD
                || metric.getCanonicalCharacteristic() == ShoeLabCharacteristic.BREATHABILITY;
    }

    private MetricDirection direction(ShoeLabCharacteristic characteristic) {
        return characteristic == ShoeLabCharacteristic.CUSHION
                ? MetricDirection.LOWER_IS_HIGH
                : MetricDirection.HIGHER_IS_HIGH;
    }

    private boolean same(String left, String right) {
        return Objects.equals(normalize(left), normalize(right));
    }

    private boolean sameSourceMetric(String left, String right) {
        return Objects.equals(sourceMetricBase(left), sourceMetricBase(right));
    }

    private String sourceMetricBase(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        return normalized.replaceFirst("\\s*\\((?:new|old) method\\)\\s*$", "");
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String normalizedOrEmpty(String value) {
        String normalized = normalize(value);
        return normalized == null ? "" : normalized;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private record Bounds(BigDecimal minimum, BigDecimal maximum) {
    }
}
