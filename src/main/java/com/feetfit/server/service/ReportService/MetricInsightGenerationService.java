package com.feetfit.server.service.ReportService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feetfit.server.domain.MetricAnalysisResult;
import com.feetfit.server.domain.enums.GaugeStatus;
import com.feetfit.server.domain.enums.MetricType;
import com.feetfit.server.repository.MetricAnalysisResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricInsightGenerationService {

    private static final String OPENAI_CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-4.1-mini";
    private static final int HISTORY_LIMIT = 10;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final MetricAnalysisResultRepository metricAnalysisResultRepository;

    @Value("${openai.api-key:${OPENAI_API_KEY:}}")
    private String openAiApiKey;

    @Value("${openai.metric-insight.model:${openai.care-tips.model:" + DEFAULT_MODEL + "}}")
    private String metricInsightModel;

    public MetricInsight generate(
            Long userId,
            MetricType metricType,
            Float currentScore,
            List<String> currentAnalysisAdvice,
            GaugeStatus fallbackStatus
    ) {
        MetricInsight fallback = new MetricInsight(fallbackStatus, currentAnalysisAdvice);
        if (!StringUtils.hasText(openAiApiKey)) {
            log.warn("Metric insight generation skipped because OPENAI_API_KEY is not configured. userId={}, metricType={}",
                    userId, metricType);
            return fallback;
        }

        try {
            LocalDateTime todayStart = LocalDate.now(ZoneId.of("Asia/Seoul")).atStartOfDay();
            List<HistoricalMetricResult> history = metricAnalysisResultRepository
                    .findRecentCompletedBeforeToday(
                            userId,
                            metricType,
                            todayStart,
                            PageRequest.of(0, HISTORY_LIMIT))
                    .stream()
                    .map(this::toHistoricalResult)
                    .toList();

            String prompt = buildPrompt(metricType, currentScore, currentAnalysisAdvice, history);
            return requestMetricInsight(prompt);
        } catch (Exception exception) {
            log.error("Metric insight generation failed; using existing status and advice. userId={}, metricType={}",
                    userId, metricType, exception);
            return fallback;
        }
    }

    private HistoricalMetricResult toHistoricalResult(MetricAnalysisResult result) {
        return new HistoricalMetricResult(
                result.getReport().getReportDate(),
                result.getScore(),
                result.getStatus(),
                result.getAdvice());
    }

    private MetricInsight requestMetricInsight(String prompt) throws Exception {
        JsonNode response = webClient.post()
                .uri(OPENAI_CHAT_COMPLETIONS_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(buildOpenAiRequest(prompt))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(30));

        String content = response == null
                ? null
                : response.path("choices").path(0).path("message").path("content").asText(null);
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("OpenAI metric insight response content is empty.");
        }
        return parseMetricInsight(content);
    }

    private Map<String, Object> buildOpenAiRequest(String prompt) {
        return Map.of(
                "model", StringUtils.hasText(metricInsightModel) ? metricInsightModel : DEFAULT_MODEL,
                "temperature", 0.2,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", """
                                        너는 발 건강 리포트의 비의료적 분석 문구 작성자다.
                                        제공된 오늘 결과와 과거 결과만 사용하고 질병을 진단하거나 확정하지 않는다.
                                        반드시 지정된 JSON 객체만 반환한다.
                                        """),
                        Map.of("role", "user", "content", prompt)));
    }

    String buildPrompt(
            MetricType metricType,
            Float currentScore,
            List<String> currentAnalysisAdvice,
            List<HistoricalMetricResult> history
    ) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
                오늘의 발 건강 지표를 평가하여 status와 advice를 생성해.

                반환 형식:
                {
                  "status": "VERY_GOOD | ATTENTION_NEEDED | NEED_IMPROVEMENT",
                  "advice": ["첫 번째 문장", "두 번째 문장"]
                }

                판단 규칙:
                - 점수는 높을수록 좋은 상태를 의미함
                - 과거 결과가 있으면 오늘 점수와 최근 최대 10회 점수의 수준 및 변화 추이를 함께 비교
                - 과거 결과가 없으면 오늘 점수와 오늘 원본 분석 설명만으로 판단
                - VERY_GOOD: 현재 상태와 최근 변화가 전반적으로 양호함
                - ATTENTION_NEEDED: 현재 상태가 보통이거나 최근 하락 추이가 있어 주의가 필요함
                - NEED_IMPROVEMENT: 현재 상태가 좋지 않거나 의미 있는 악화가 확인됨
                - advice는 정확히 2개의 한국어 문장으로 작성
                - 첫 문장은 현재 상태와 과거 대비 변화를 설명하고, 두 번째 문장은 실천 가능한 관리 방법을 제시
                - 과거가 없으면 이전 측정과 비교하는 표현을 사용하지 않음
                - 과장, 진단, 치료 확정 표현 금지

                오늘 결과:
                """);
        prompt.append("- metricType: ").append(metricType).append('\n');
        prompt.append("- score: ").append(currentScore).append('\n');
        prompt.append("- 원본 분석 설명: ").append(currentAnalysisAdvice).append('\n');

        if (history.isEmpty()) {
            prompt.append("\n과거 결과: 없음 (오늘이 첫 분석)\n");
        } else {
            prompt.append("\n오늘을 제외한 최근 분석 결과 (최신순, ")
                    .append(history.size()).append("개):\n");
            for (int index = 0; index < history.size(); index++) {
                HistoricalMetricResult item = history.get(index);
                prompt.append(index + 1).append(". 날짜=").append(item.reportDate())
                        .append(", score=").append(item.score())
                        .append(", status=").append(item.status())
                        .append(", advice=").append(item.advice())
                        .append('\n');
            }
        }
        return prompt.toString();
    }

    private MetricInsight parseMetricInsight(String content) throws Exception {
        JsonNode root = objectMapper.readTree(stripCodeFence(content));
        GaugeStatus status = GaugeStatus.valueOf(root.path("status").asText(""));
        List<String> advice = new ArrayList<>();
        JsonNode adviceNode = root.path("advice");
        if (adviceNode.isArray()) {
            adviceNode.forEach(node -> {
                String value = node.asText(null);
                if (StringUtils.hasText(value)) {
                    advice.add(value.trim());
                }
            });
        }
        if (advice.size() != 2) {
            throw new IllegalStateException("OpenAI metric insight advice must contain exactly two items.");
        }
        return new MetricInsight(status, List.copyOf(advice));
    }

    private String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```json") && trimmed.endsWith("```")) {
            return trimmed.substring(7, trimmed.length() - 3).trim();
        }
        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            return trimmed.substring(3, trimmed.length() - 3).trim();
        }
        return trimmed;
    }

    public record MetricInsight(GaugeStatus status, List<String> advice) {
    }

    record HistoricalMetricResult(
            LocalDateTime reportDate,
            Float score,
            GaugeStatus status,
            List<String> advice
    ) {
    }
}
