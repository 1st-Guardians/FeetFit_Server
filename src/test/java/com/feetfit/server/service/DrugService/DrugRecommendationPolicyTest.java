package com.feetfit.server.service.DrugService;

import com.feetfit.server.domain.enums.DrugCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DrugRecommendationPolicyTest {
    private final DrugRecommendationPolicy policy = new DrugRecommendationPolicy();

    @ParameterizedTest
    @CsvSource({"0,1,0", "15,1,0", "15.01,2,33", "20,2,33", "20.01,3,67", "40,3,67", "40.01,4,100"})
    void usesProvidedHvaFunctionBoundariesAndWorseFoot(float angle, int expectedLevel, double expectedPriority) {
        var hva = policy.rank(100f, 100f, 0f, angle).priorities().stream()
                .filter(value -> value.category() == DrugCategory.BUNION_BAND).findFirst().orElseThrow();
        assertThat(hva.hvaLevel()).isEqualTo(expectedLevel);
        assertThat(hva.priorityScore()).isEqualTo(expectedPriority);
        assertThat(hva.leftToeAngleDegree()).isEqualTo(0);
        assertThat(hva.rightToeAngleDegree()).isEqualTo((double) angle);
    }

    @Test
    void lowerSafetyScoreIsWeakerAndComparedWithAngleBandPoints() {
        var basis = policy.rank(20f, 70f, 30f, 8f);
        assertThat(basis.priorities()).extracting(DrugRecommendationPolicy.Priority::category)
                .containsExactly(DrugCategory.WOUND_CARE, DrugCategory.BUNION_BAND, DrugCategory.ATHLETES_FOOT);
        assertThat(basis.priorities()).extracting(DrugRecommendationPolicy.Priority::priorityScore)
                .containsExactly(80.0, 67.0, 30.0);
    }

    @Test
    void severeAngleCanBeHighestPriorityWithoutUsingInvertedRiskScore() {
        var basis = policy.rank(90f, 80f, 45f, 12f);
        assertThat(basis.priorities().get(0).category()).isEqualTo(DrugCategory.BUNION_BAND);
        assertThat(basis.priorities().get(0).hvaLevel()).isEqualTo(4);
    }

    @Test
    void allHealthyDoesNotImplyMedicationIsNeeded() {
        var basis = policy.rank(100f, 100f, 15f, 5f);
        assertThat(basis.allHealthy()).isTrue();
        assertThat(basis.priorities()).extracting(DrugRecommendationPolicy.Priority::category)
                .containsExactly(DrugCategory.WOUND_CARE, DrugCategory.ATHLETES_FOOT, DrugCategory.BUNION_BAND);
        assertThat(basis.priorities()).extracting(DrugRecommendationPolicy.Priority::priorityScore)
                .containsOnly(0.0);
        assertThat(policy.fallbackSummary(basis)).isEqualTo(
                        "측정 지표가 전반적으로 양호하게 나타났어요. 아래 제품은 필요할 때 참고하실 수 있도록 추천드리며, 현재 측정 결과가 별도의 약 사용이 필요하다는 의미는 아니에요.")
                .isEqualTo(DrugRecommendationPolicy.HEALTHY_SUMMARY)
                .doesNotContain("발 관리 제품 정보를 안내");
    }

    @ParameterizedTest
    @CsvSource({"90,10,10,무좀약", "10,90,10,상처 보호", "100,100,45,발가락 보호", "100,100,15,양호"})
    void fallbackSummaryIsShortAndDoesNotMentionMeasurements(float skin, float fungal, float angle, String product) {
        String summary = policy.fallbackSummary(policy.rank(skin, fungal, angle, 5f));

        assertThat(summary).contains(product).doesNotContain("점수", "각도", "단계", "°", "%");
        assertThat(summary.codePoints().anyMatch(Character::isDigit)).isFalse();
        assertThat(summary.codePointCount(0, summary.length())).isBetween(
                DrugRecommendationSummaryClient.MIN_SUMMARY_LENGTH, DrugRecommendationSummaryClient.MAX_SUMMARY_LENGTH);
    }

    @Test
    void missingOrInvalidInputsAreUnassessedAndSortLast() {
        var basis = policy.rank(null, 60f, Float.NaN, -5f);
        assertThat(basis.allHealthy()).isFalse();
        assertThat(basis.priorities().get(0).category()).isEqualTo(DrugCategory.ATHLETES_FOOT);
        assertThat(basis.priorities().get(1).priorityScore()).isNull();
        assertThat(basis.priorities().get(2).priorityScore()).isNull();
        assertThat(policy.rank(Float.NaN, Float.POSITIVE_INFINITY, null, null).priorities())
                .allMatch(item -> item.priorityScore() == null);
        assertThat(policy.rank(null, null, null, null).allHealthy()).isFalse();
    }

    @Test
    void tiesHaveStableOrderRatherThanChangingOnEachRequest() {
        assertThat(policy.rank(50f, 50f, 10f, 10f).priorities())
                .extracting(DrugRecommendationPolicy.Priority::category)
                .containsExactly(DrugCategory.ATHLETES_FOOT, DrugCategory.WOUND_CARE, DrugCategory.BUNION_BAND);
    }
}
