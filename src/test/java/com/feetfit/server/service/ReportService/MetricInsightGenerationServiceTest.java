package com.feetfit.server.service.ReportService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feetfit.server.domain.enums.GaugeStatus;
import com.feetfit.server.domain.enums.MetricType;
import com.feetfit.server.repository.MetricAnalysisResultRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MetricInsightGenerationServiceTest {

    private final MetricInsightGenerationService service = new MetricInsightGenerationService(
            WebClient.builder().build(),
            new ObjectMapper(),
            mock(MetricAnalysisResultRepository.class));

    @Test
    void buildPrompt_withoutHistory_usesOnlyCurrentResult() {
        String prompt = service.buildPrompt(
                MetricType.FOOT_ENVIRONMENT,
                61.0f,
                List.of("오늘 분석 설명 1", "오늘 분석 설명 2"),
                List.of());

        assertThat(prompt)
                .contains("과거 결과: 없음 (오늘이 첫 분석)")
                .contains("score: 61.0")
                .contains("과거가 없으면 이전 측정과 비교하는 표현을 사용하지 않음");
    }

    @Test
    void buildPrompt_withHistory_includesTenResultsInNewestFirstOrder() {
        List<MetricInsightGenerationService.HistoricalMetricResult> history = IntStream.rangeClosed(1, 10)
                .mapToObj(index -> new MetricInsightGenerationService.HistoricalMetricResult(
                        LocalDateTime.of(2026, 8, 11 - index, 10, 0),
                        80.0f - index,
                        GaugeStatus.VERY_GOOD,
                        List.of("과거 설명 1", "과거 설명 2")))
                .toList();

        String prompt = service.buildPrompt(
                MetricType.PRESSURE_BALANCE,
                72.0f,
                List.of("오늘 설명 1", "오늘 설명 2"),
                history);

        assertThat(prompt)
                .contains("오늘을 제외한 최근 분석 결과 (최신순, 10개)")
                .contains("1. 날짜=2026-08-10T10:00")
                .contains("10. 날짜=2026-08-01T10:00");
    }
}
