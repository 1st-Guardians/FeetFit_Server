package com.feetfit.server.service.ShoeService;

import com.feetfit.server.domain.enums.MetricDirection;
import com.feetfit.server.domain.enums.ShoeCharacteristicLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CharacteristicLevelPolicyTest {

    private final CharacteristicLevelPolicy policy = new CharacteristicLevelPolicy();

    @Test
    void classifiesValueAtP33AsLow() {
        assertThat(policy.determineLevel(
                decimal("19.9"),
                distributionWithInterpolatedP33AndP67(),
                MetricDirection.HIGHER_IS_HIGH))
                .isEqualTo(ShoeCharacteristicLevel.LOW);
    }

    @Test
    void classifiesValueAboveP33AndBelowP67AsMedium() {
        assertThat(policy.determineLevel(
                decimal("20"),
                distributionWithInterpolatedP33AndP67(),
                MetricDirection.HIGHER_IS_HIGH))
                .isEqualTo(ShoeCharacteristicLevel.MEDIUM);
    }

    @Test
    void classifiesValueAtP67AsHigh() {
        assertThat(policy.determineLevel(
                decimal("30.1"),
                distributionWithInterpolatedP33AndP67(),
                MetricDirection.HIGHER_IS_HIGH))
                .isEqualTo(ShoeCharacteristicLevel.HIGH);
    }

    @Test
    void usesLinearInterpolationRatherThanNearestRank() {
        List<BigDecimal> values = List.of(decimal("0"), decimal("10"), decimal("20"), decimal("30"));

        assertThat(policy.determineLevel(
                decimal("9.9"), values, MetricDirection.HIGHER_IS_HIGH))
                .isEqualTo(ShoeCharacteristicLevel.LOW);
        assertThat(policy.determineLevel(
                decimal("20.1"), values, MetricDirection.HIGHER_IS_HIGH))
                .isEqualTo(ShoeCharacteristicLevel.HIGH);
    }

    @Test
    void reversesLowAndHighForCushionSoftness() {
        List<BigDecimal> hardnessValues = List.of(
                decimal("20"), decimal("30"), decimal("40"), decimal("50"));

        assertThat(policy.determineLevel(
                decimal("20"), hardnessValues, MetricDirection.LOWER_IS_HIGH))
                .isEqualTo(ShoeCharacteristicLevel.HIGH);
        assertThat(policy.determineLevel(
                decimal("50"), hardnessValues, MetricDirection.LOWER_IS_HIGH))
                .isEqualTo(ShoeCharacteristicLevel.LOW);
    }

    @Test
    void keepsMediumForInverseMetric() {
        assertThat(policy.determineLevel(
                decimal("25"),
                distributionWithInterpolatedP33AndP67(),
                MetricDirection.LOWER_IS_HIGH))
                .isEqualTo(ShoeCharacteristicLevel.MEDIUM);
    }

    @Test
    void returnsNullWhenCohortCannotPopulateThreeBands() {
        assertThat(policy.determineLevel(
                decimal("10"), List.of(decimal("10")), MetricDirection.HIGHER_IS_HIGH))
                .isNull();
        assertThat(policy.determineLevel(
                decimal("10"), List.of(decimal("10"), decimal("20")),
                MetricDirection.HIGHER_IS_HIGH))
                .isNull();
    }

    @Test
    void returnsNullForMissingTargetOrEmptyDistribution() {
        assertThat(policy.determineLevel(
                null, List.of(decimal("10")), MetricDirection.HIGHER_IS_HIGH))
                .isNull();
        assertThat(policy.determineLevel(
                decimal("10"), List.of(), MetricDirection.HIGHER_IS_HIGH))
                .isNull();
    }

    @Test
    void ignoresNullSamplesAndRequiresThreeUsableSamples() {
        assertThat(policy.determineLevel(
                decimal("10"),
                java.util.Arrays.asList(null, decimal("10"), decimal("20"), decimal("30"), null),
                MetricDirection.HIGHER_IS_HIGH))
                .isEqualTo(ShoeCharacteristicLevel.LOW);
        assertThat(policy.determineLevel(
                decimal("10"),
                java.util.Arrays.asList(null, null),
                MetricDirection.HIGHER_IS_HIGH))
                .isNull();
    }

    @Test
    void returnsNullWhenPercentileBoundariesCollapse() {
        List<BigDecimal> tied = List.of(decimal("3"), decimal("3"), decimal("3"));

        assertThat(policy.determineLevel(
                decimal("3"), tied, MetricDirection.HIGHER_IS_HIGH)).isNull();
        assertThat(policy.determineLevel(
                decimal("3"), tied, MetricDirection.LOWER_IS_HIGH)).isNull();
    }

    private List<BigDecimal> distributionWithInterpolatedP33AndP67() {
        // Sorted [10, 20, 30, 40]: P33 = 19.9 and P67 = 30.1.
        return List.of(decimal("40"), decimal("10"), decimal("30"), decimal("20"));
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
