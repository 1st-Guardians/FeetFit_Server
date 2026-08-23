package com.feetfit.server.service.ShoeService;

import com.feetfit.server.domain.enums.ShoeCharacteristicLevel;
import com.feetfit.server.domain.enums.ShoeLabCharacteristic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ShoeCharacteristicSummaryServiceTest {

    private ShoeCharacteristicSummaryService service;

    @BeforeEach
    void setUp() {
        service = new ShoeCharacteristicSummaryService();
    }

    @Test
    void summarizesAllAvailableGroupsAsNaturalProductFacts() {
        Map<ShoeLabCharacteristic, ShoeCharacteristicLevel> levels =
                new EnumMap<>(ShoeLabCharacteristic.class);
        levels.put(ShoeLabCharacteristic.CUSHION, ShoeCharacteristicLevel.HIGH);
        levels.put(ShoeLabCharacteristic.SHOCK_ABSORPTION, ShoeCharacteristicLevel.HIGH);
        levels.put(ShoeLabCharacteristic.ENERGY_RETURN, ShoeCharacteristicLevel.MEDIUM);
        levels.put(ShoeLabCharacteristic.WIDTH_SPACE, ShoeCharacteristicLevel.HIGH);
        levels.put(ShoeLabCharacteristic.TOEBOX_SPACE, ShoeCharacteristicLevel.LOW);
        levels.put(ShoeLabCharacteristic.HEEL_HOLD, ShoeCharacteristicLevel.MEDIUM);
        levels.put(ShoeLabCharacteristic.BREATHABILITY, ShoeCharacteristicLevel.LOW);

        assertThat(service.summarize(levels)).isEqualTo(
                "쿠션감과 충격 완화는 높은 편이고 반발력은 보통 수준인 신발입니다. "
                        + "발볼은 비교적 여유로운 편인 반면 앞코 공간은 좁은 편입니다. "
                        + "뒤꿈치 구조는 보통 수준이며 통기성은 낮은 편입니다.");
    }

    @Test
    void oneCharacteristicCreatesOnlyOneConciseSentence() {
        assertThat(service.summarize(Map.of(
                ShoeLabCharacteristic.WIDTH_SPACE,
                ShoeCharacteristicLevel.MEDIUM)))
                .isEqualTo("발볼 여유는 보통 수준인 신발입니다.");
    }

    @Test
    void missingCharacteristicsAreNotMentioned() {
        Map<ShoeLabCharacteristic, ShoeCharacteristicLevel> levels =
                new EnumMap<>(ShoeLabCharacteristic.class);
        levels.put(ShoeLabCharacteristic.CUSHION, ShoeCharacteristicLevel.HIGH);
        levels.put(ShoeLabCharacteristic.ENERGY_RETURN, null);
        levels.put(ShoeLabCharacteristic.BREATHABILITY, ShoeCharacteristicLevel.LOW);

        assertThat(service.summarize(levels))
                .isEqualTo(
                        "쿠션감은 높은 편인 신발입니다. 통기성은 낮은 편입니다.")
                .doesNotContain("반발력", "충격 완화", "발볼", "앞코", "뒤꿈치");
    }

    @Test
    void sameLevelMetricsWithinAGroupAreCombined() {
        assertThat(service.summarize(Map.of(
                ShoeLabCharacteristic.CUSHION, ShoeCharacteristicLevel.MEDIUM,
                ShoeLabCharacteristic.SHOCK_ABSORPTION, ShoeCharacteristicLevel.MEDIUM,
                ShoeLabCharacteristic.ENERGY_RETURN, ShoeCharacteristicLevel.MEDIUM)))
                .isEqualTo(
                        "쿠션감, 충격 완화와 반발력은 모두 보통 수준인 신발입니다.");
    }

    @Test
    void adjacentSpaceLevelsUseNeutralConnectionInsteadOfForcedContrast() {
        assertThat(service.summarize(Map.of(
                ShoeLabCharacteristic.WIDTH_SPACE, ShoeCharacteristicLevel.MEDIUM,
                ShoeLabCharacteristic.TOEBOX_SPACE, ShoeCharacteristicLevel.HIGH)))
                .isEqualTo(
                        "발볼은 보통 수준이며 앞코 공간은 넓은 편입니다.");
    }

    @Test
    void nullLevelsAreExcludedAndDoNotCreateSentences() {
        Map<ShoeLabCharacteristic, ShoeCharacteristicLevel> levels =
                new EnumMap<>(ShoeLabCharacteristic.class);
        levels.put(ShoeLabCharacteristic.CUSHION, null);
        levels.put(ShoeLabCharacteristic.TOEBOX_SPACE, ShoeCharacteristicLevel.MEDIUM);

        assertThat(service.summarize(levels))
                .isEqualTo("앞코 여유는 보통 수준인 신발입니다.")
                .doesNotContain("쿠션감");
    }

    @Test
    void noValidLevelReturnsNull() {
        Map<ShoeLabCharacteristic, ShoeCharacteristicLevel> onlyNull =
                new EnumMap<>(ShoeLabCharacteristic.class);
        onlyNull.put(ShoeLabCharacteristic.CUSHION, null);

        assertThat(service.summarize(null)).isNull();
        assertThat(service.summarize(Map.of())).isNull();
        assertThat(service.summarize(onlyNull)).isNull();
    }

    @Test
    void heelSummaryDescribesOnlyStructureStiffness() {
        String summary = service.summarize(Map.of(
                ShoeLabCharacteristic.HEEL_HOLD, ShoeCharacteristicLevel.HIGH));

        assertThat(summary)
                .isEqualTo("뒤꿈치 구조의 강성은 높은 편인 신발입니다.")
                .doesNotContain(
                        "고정력", "고정 성능", "안정성", "편안", "우수", "좋은",
                        "걷기", "러닝", "적합");
    }

    @Test
    void summaryNeverAddsUserFitMedicalOrActivityClaims() {
        String summary = service.summarize(Map.of(
                ShoeLabCharacteristic.CUSHION, ShoeCharacteristicLevel.HIGH,
                ShoeLabCharacteristic.WIDTH_SPACE, ShoeCharacteristicLevel.HIGH,
                ShoeLabCharacteristic.BREATHABILITY, ShoeCharacteristicLevel.LOW));

        assertThat(summary)
                .doesNotContain(
                        "사용자", "내 발", "평발", "통증", "아프", "장시간",
                        "러닝", "걷기", "적합", "추천");
    }
}
