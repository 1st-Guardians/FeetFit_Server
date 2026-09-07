package com.feetfit.server.converter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportConverterTest {

    @Test
    void generateHvaAnalysisText_doesNotExposeHvaLabel() {
        assertThat(ReportConverter.generateHvaAnalysisText(4.3f))
                .isEqualTo("엄지발가락이 두 번째 발가락 쪽으로 기울어진 각도가 4.3°로 측정되었습니다. 정상 기준(15° 이하)에 해당합니다.")
                .doesNotContain("(HVA)");
    }
}
