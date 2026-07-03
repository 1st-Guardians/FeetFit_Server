package com.feetfit.server.service.FootOdourService;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FootOdourCalculatorTest {

    @Test
    void 낮음_0_20ppm() {
        // 200ppb raw, 0 baseline → 0.20ppm → 낮음
        var result = FootOdourCalculator.calculate(200, 0);

        assertThat(result.displayPpm()).isCloseTo(0.20f, within(0.001f));
        assertThat(result.rawPpm()).isCloseTo(0.20f, within(0.001f));
        assertThat(result.comment()).contains("0.20ppm").contains("낮은 편이에요");
    }

    @Test
    void 보통_0_50ppm() {
        // 500ppb raw, 0 baseline → 0.50ppm → 보통
        var result = FootOdourCalculator.calculate(500, 0);

        assertThat(result.displayPpm()).isCloseTo(0.50f, within(0.001f));
        assertThat(result.comment()).contains("0.50ppm").contains("보통 수준이에요");
    }

    @Test
    void 높음_2_00ppm() {
        // 2000ppb raw, 0 baseline → 2.00ppm → 높음
        var result = FootOdourCalculator.calculate(2000, 0);

        assertThat(result.displayPpm()).isCloseTo(2.00f, within(0.001f));
        assertThat(result.comment()).contains("2.00ppm").contains("높은 편이에요");
    }

    @Test
    void 매우높음_4_00ppm() {
        // 4000ppb raw, 0 baseline → 4.00ppm → 매우 높음
        var result = FootOdourCalculator.calculate(4000, 0);

        assertThat(result.displayPpm()).isCloseTo(4.00f, within(0.001f));
        assertThat(result.comment()).contains("4.00ppm").contains("매우 높은 편이에요");
    }

    @Test
    void clamp_7_00ppm_raw_display는_5_00() {
        // 7000ppb raw, 0 baseline → rawPpm=7.00, displayPpm=5.00
        var result = FootOdourCalculator.calculate(7000, 0);

        assertThat(result.rawPpm()).isCloseTo(7.00f, within(0.001f));
        assertThat(result.displayPpm()).isCloseTo(5.00f, within(0.001f));
        assertThat(result.comment()).contains("5.00ppm").contains("매우 높은 편이에요");
    }

    @Test
    void baseline이_더_크면_0ppm() {
        // baseline > tvoc → corrected=0 → 0.00ppm
        var result = FootOdourCalculator.calculate(500, 1000);

        assertThat(result.displayPpm()).isCloseTo(0.00f, within(0.001f));
        assertThat(result.rawPpm()).isCloseTo(0.00f, within(0.001f));
        assertThat(result.comment()).contains("낮은 편이에요");
    }

    @Test
    void tvocPpb_null_이면_0ppm() {
        var result = FootOdourCalculator.calculate(null, null);

        assertThat(result.displayPpm()).isCloseTo(0.00f, within(0.001f));
        assertThat(result.comment()).contains("낮은 편이에요");
    }

    @Test
    void 음수_입력_방어() {
        // 음수 tvoc → 0 처리
        var result = FootOdourCalculator.calculate(-500, -200);

        assertThat(result.displayPpm()).isCloseTo(0.00f, within(0.001f));
    }

    @Test
    void baseline만_음수_방어() {
        // baseline 음수 → 0으로 처리 → tvoc 그대로 사용
        var result = FootOdourCalculator.calculate(300, -100);

        assertThat(result.displayPpm()).isCloseTo(0.30f, within(0.001f));
    }
}
