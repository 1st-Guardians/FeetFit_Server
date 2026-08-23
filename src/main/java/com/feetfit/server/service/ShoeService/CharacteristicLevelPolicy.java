package com.feetfit.server.service.ShoeService;

import com.feetfit.server.domain.enums.MetricDirection;
import com.feetfit.server.domain.enums.ShoeCharacteristicLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Converts a raw metric value into a relative LOW/MEDIUM/HIGH level.
 *
 * <p>The caller must provide values from one compatible comparison cohort: the
 * same canonical characteristic, unit, method version and location, together
 * with a compatible {@code comparisonCohort}. This class intentionally does not
 * combine or validate those dimensions.</p>
 *
 * <p>P33 and P67 use linear interpolation at index {@code p * (n - 1)} on the
 * sorted values. The minimum cohort size is controlled by
 * {@code shoe.characteristics.minimum-cohort-size}; its default of three is
 * the smallest cohort that can populate all three requested bands. A missing
 * target, smaller cohort, or collapsed P33/P67 boundary produces {@code null}.</p>
 */
@Component
public class CharacteristicLevelPolicy {

    private static final BigDecimal P33 = new BigDecimal("0.33");
    private static final BigDecimal P67 = new BigDecimal("0.67");
    private static final int DEFAULT_MINIMUM_COHORT_SIZE = 3;

    private final int minimumCohortSize;

    public CharacteristicLevelPolicy() {
        this(DEFAULT_MINIMUM_COHORT_SIZE);
    }

    @Autowired
    public CharacteristicLevelPolicy(
            @Value("${shoe.characteristics.minimum-cohort-size:3}") int minimumCohortSize) {
        if (minimumCohortSize < 3) {
            throw new IllegalArgumentException(
                    "shoe characteristics minimum cohort size must be at least 3");
        }
        this.minimumCohortSize = minimumCohortSize;
    }

    /**
     * Applies the percentile boundaries and then the metric direction.
     *
     * <p>For a direct metric, values at or below P33 are LOW, values above P33
     * and below P67 are MEDIUM, and values at or above P67 are HIGH. An inverse
     * metric reverses LOW and HIGH after applying those raw-value boundaries.
     * This makes lower midsole hardness values map to higher CUSHION levels.</p>
     *
     * @return the relative level, or {@code null} if the target or usable
     * distribution has fewer than three usable observations
     */
    public ShoeCharacteristicLevel determineLevel(
            BigDecimal target,
            List<BigDecimal> distribution,
            MetricDirection direction) {
        Objects.requireNonNull(direction, "direction must not be null");

        if (target == null || distribution == null) {
            return null;
        }

        List<BigDecimal> sortedValues = distribution.stream()
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        if (!hasSufficientCohort(sortedValues)) {
            return null;
        }

        BigDecimal p33 = percentile(sortedValues, P33);
        BigDecimal p67 = percentile(sortedValues, P67);
        if (p33.compareTo(p67) >= 0) {
            return null;
        }

        ShoeCharacteristicLevel rawLevel;
        if (target.compareTo(p33) <= 0) {
            rawLevel = ShoeCharacteristicLevel.LOW;
        } else if (target.compareTo(p67) < 0) {
            rawLevel = ShoeCharacteristicLevel.MEDIUM;
        } else {
            rawLevel = ShoeCharacteristicLevel.HIGH;
        }

        return direction.applyTo(rawLevel);
    }

    public boolean hasSufficientCohort(List<BigDecimal> distribution) {
        return distribution != null
                && distribution.stream().filter(Objects::nonNull).count() >= minimumCohortSize;
    }

    private BigDecimal percentile(List<BigDecimal> sortedValues, BigDecimal percentile) {
        if (sortedValues.size() == 1) {
            return sortedValues.get(0);
        }

        BigDecimal index = percentile.multiply(BigDecimal.valueOf(sortedValues.size() - 1L));
        int lowerIndex = index.setScale(0, RoundingMode.FLOOR).intValueExact();
        int upperIndex = index.setScale(0, RoundingMode.CEILING).intValueExact();
        BigDecimal lowerValue = sortedValues.get(lowerIndex);

        if (lowerIndex == upperIndex) {
            return lowerValue;
        }

        BigDecimal fraction = index.subtract(BigDecimal.valueOf(lowerIndex));
        BigDecimal upperValue = sortedValues.get(upperIndex);
        return lowerValue.add(upperValue.subtract(lowerValue).multiply(fraction));
    }
}
