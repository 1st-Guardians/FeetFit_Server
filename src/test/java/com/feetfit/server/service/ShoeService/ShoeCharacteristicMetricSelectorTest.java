package com.feetfit.server.service.ShoeService;

import com.feetfit.server.domain.ShoeLabMetric;
import com.feetfit.server.domain.enums.ShoeLabCharacteristic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ShoeCharacteristicMetricSelectorTest {

    private final ShoeCharacteristicMetricSelector selector =
            new ShoeCharacteristicMetricSelector();

    @ParameterizedTest(name = "{0} uses only its explicitly supported raw metric")
    @MethodSource("displayMetrics")
    void selectsEachCanonicalDisplayMetric(
            ShoeLabCharacteristic characteristic,
            ShoeLabMetric expected) {
        ShoeLabMetric unrelated = metric(
                ShoeLabCharacteristic.CUSHION,
                "Midsole softness",
                "AC",
                null,
                "secondary",
                "99");

        assertThat(selector.select(characteristic, List.of(unrelated, expected)))
                .containsSame(expected);
    }

    @Test
    void prefersCurrentAcSoftnessOverLegacyHaWithoutCombiningThem() {
        ShoeLabMetric ha = metric(
                ShoeLabCharacteristic.CUSHION,
                "Midsole softness",
                "HA",
                null,
                "primary",
                "35");
        ShoeLabMetric ac = metric(
                ShoeLabCharacteristic.CUSHION,
                "Midsole softness",
                "AC",
                null,
                "primary",
                "29");

        assertThat(selector.select(ShoeLabCharacteristic.CUSHION, List.of(ha, ac)))
                .containsSame(ac);
        assertThat(selector.select(ShoeLabCharacteristic.CUSHION, List.of(ha)))
                .containsSame(ha);
    }

    @Test
    void prefersCurrentBrBreathabilityOverLegacyScoreWithoutCombiningThem() {
        ShoeLabMetric score = metric(
                ShoeLabCharacteristic.BREATHABILITY,
                "Breathability",
                "score",
                null,
                null,
                "4");
        ShoeLabMetric br = metric(
                ShoeLabCharacteristic.BREATHABILITY,
                "Breathability",
                "BR",
                null,
                null,
                "132");

        assertThat(selector.select(ShoeLabCharacteristic.BREATHABILITY, List.of(score, br)))
                .containsSame(br);
        assertThat(selector.select(ShoeLabCharacteristic.BREATHABILITY, List.of(score)))
                .containsSame(score);
    }

    @Test
    void neverSubstitutesForefootForHeelShockOrEnergyReturn() {
        ShoeLabMetric forefootShock = metric(
                ShoeLabCharacteristic.SHOCK_ABSORPTION,
                "Shock absorption forefoot",
                "SA",
                "FOREFOOT",
                null,
                "95");
        ShoeLabMetric forefootEnergy = metric(
                ShoeLabCharacteristic.ENERGY_RETURN,
                "Energy return forefoot",
                "%",
                "FOREFOOT",
                null,
                "63");

        assertThat(selector.select(
                ShoeLabCharacteristic.SHOCK_ABSORPTION, List.of(forefootShock)))
                .isEmpty();
        assertThat(selector.select(
                ShoeLabCharacteristic.ENERGY_RETURN, List.of(forefootEnergy)))
                .isEmpty();
    }

    @Test
    void requiresTheActualHeelSourceMetricNameEvenWhenLocationAndUnitLookValid() {
        ShoeLabMetric genericShock = metric(
                ShoeLabCharacteristic.SHOCK_ABSORPTION,
                "Shock absorption",
                "SA",
                "HEEL",
                null,
                "120");
        ShoeLabMetric genericEnergy = metric(
                ShoeLabCharacteristic.ENERGY_RETURN,
                "Energy return",
                "%",
                "HEEL",
                null,
                "70");

        assertThat(selector.select(
                ShoeLabCharacteristic.SHOCK_ABSORPTION, List.of(genericShock)))
                .isEmpty();
        assertThat(selector.select(
                ShoeLabCharacteristic.ENERGY_RETURN, List.of(genericEnergy)))
                .isEmpty();
    }

    @Test
    void acceptsRunRepeatMethodSuffixWhileComparingTheSourceBaseName() {
        ShoeLabMetric newSoftness = metric(
                ShoeLabCharacteristic.CUSHION,
                "Midsole softness (new method)",
                "AC",
                null,
                "primary",
                "29");
        ShoeLabMetric oldBreathability = metric(
                ShoeLabCharacteristic.BREATHABILITY,
                "Breathability (old method)",
                "score",
                null,
                null,
                "3");

        assertThat(selector.select(ShoeLabCharacteristic.CUSHION, List.of(newSoftness)))
                .containsSame(newSoftness);
        assertThat(selector.select(
                ShoeLabCharacteristic.BREATHABILITY, List.of(oldBreathability)))
                .containsSame(oldBreathability);
    }

    @Test
    void neverUsesToeboxHeightAsTheToeboxSpaceRepresentative() {
        ShoeLabMetric height = metric(
                ShoeLabCharacteristic.TOEBOX_SPACE,
                "Toebox height",
                "mm",
                null,
                "height",
                "27");

        assertThat(selector.select(ShoeLabCharacteristic.TOEBOX_SPACE, List.of(height)))
                .isEmpty();
    }

    @Test
    void failsClosedWhenTwoMetricsHaveTheSameBestPriority() {
        ShoeLabMetric first = metric(
                ShoeLabCharacteristic.WIDTH_SPACE,
                "Width / Fit",
                "mm",
                null,
                "primary",
                "93.4");
        ShoeLabMetric duplicate = metric(
                ShoeLabCharacteristic.WIDTH_SPACE,
                "Width / Fit",
                "mm",
                null,
                "primary",
                "94.1");

        assertThat(selector.select(
                ShoeLabCharacteristic.WIDTH_SPACE, List.of(first, duplicate)))
                .isEmpty();
    }

    private static Stream<Arguments> displayMetrics() {
        return Stream.of(
                Arguments.of(ShoeLabCharacteristic.CUSHION, metric(
                        ShoeLabCharacteristic.CUSHION,
                        "Midsole softness", "AC", null, "primary", "29.5")),
                Arguments.of(ShoeLabCharacteristic.SHOCK_ABSORPTION, metric(
                        ShoeLabCharacteristic.SHOCK_ABSORPTION,
                        "Shock absorption heel", "SA", "HEEL", null, "132")),
                Arguments.of(ShoeLabCharacteristic.ENERGY_RETURN, metric(
                        ShoeLabCharacteristic.ENERGY_RETURN,
                        "Energy return heel", "%", "HEEL", null, "71")),
                Arguments.of(ShoeLabCharacteristic.WIDTH_SPACE, metric(
                        ShoeLabCharacteristic.WIDTH_SPACE,
                        "Width / Fit", "mm", null, "primary", "93.4")),
                Arguments.of(ShoeLabCharacteristic.TOEBOX_SPACE, metric(
                        ShoeLabCharacteristic.TOEBOX_SPACE,
                        "Toebox width", "mm", null, "width", "72.1")),
                Arguments.of(ShoeLabCharacteristic.HEEL_HOLD, metric(
                        ShoeLabCharacteristic.HEEL_HOLD,
                        "Heel counter stiffness", "score", "HEEL", null, "4")),
                Arguments.of(ShoeLabCharacteristic.BREATHABILITY, metric(
                        ShoeLabCharacteristic.BREATHABILITY,
                        "Breathability", "BR", null, null, "128"))
        );
    }

    private static ShoeLabMetric metric(
            ShoeLabCharacteristic characteristic,
            String sourceMetricName,
            String unit,
            String location,
            String variant,
            String value) {
        return ShoeLabMetric.builder()
                .canonicalCharacteristic(characteristic)
                .sourceMetricName(sourceMetricName)
                .value(new BigDecimal(value))
                .unit(unit)
                .location(location)
                .variant(variant)
                .build();
    }
}
