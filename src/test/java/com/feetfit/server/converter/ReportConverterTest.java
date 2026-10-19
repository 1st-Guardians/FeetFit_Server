package com.feetfit.server.converter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ReportConverterTest {

    @Test
    void generateHvaAnalysisText_doesNotExposeHvaLabel() {
        assertThat(ReportConverter.generateHvaAnalysisText(4.3f))
                .isEqualTo("엄지발가락이 두 번째 발가락 쪽으로 기울어진 각도가 4.3°로 측정되었습니다. 정상 기준(15° 이하)에 해당합니다.")
                .doesNotContain("(HVA)");
    }

    @Test
    void calculateHvaRiskScore_mapsEachStageBoundaryToConfiguredScore() {
        assertThat(ReportConverter.calculateHvaRiskScore(0f, 0f)).isEqualTo(100f);
        assertThat(ReportConverter.calculateHvaRiskScore(15f, 15f)).isEqualTo(90f);
        assertThat(ReportConverter.calculateHvaRiskScore(20f, 20f)).isEqualTo(70f);
        assertThat(ReportConverter.calculateHvaRiskScore(40f, 40f)).isEqualTo(40f);
        assertThat(ReportConverter.calculateHvaRiskScore(90f, 90f)).isEqualTo(0f);
        assertThat(ReportConverter.calculateHvaRiskScore(100f, 100f)).isEqualTo(0f);
    }

    @Test
    void calculateHvaRiskScore_interpolatesWithinNormalStage() {
        assertThat(ReportConverter.calculateHvaRiskScore(10f, 11f))
                .isCloseTo(93f, within(0.001f));
    }

    @Test
    void calculateHvaRiskScore_averagesLeftAndRightScores() {
        assertThat(ReportConverter.calculateHvaRiskScore(15f, 20f)).isEqualTo(80f);
    }
}
