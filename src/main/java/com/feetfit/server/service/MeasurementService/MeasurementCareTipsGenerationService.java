package com.feetfit.server.service.MeasurementService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feetfit.server.domain.DailyFootAnalysis;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.MetricAnalysisResult;
import com.feetfit.server.domain.Report;
import com.feetfit.server.domain.enums.MetricType;
import com.feetfit.server.repository.DailyFootAnalysisRepository;
import com.feetfit.server.repository.MetricAnalysisResultRepository;
import com.feetfit.server.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeasurementCareTipsGenerationService {

    private static final String OPENAI_CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-4.1-mini";
    private static final int MAX_CARE_TIPS_REQUEST_ATTEMPTS = 2;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final DailyFootAnalysisRepository dailyFootAnalysisRepository;
    private final ReportRepository reportRepository;
    private final MetricAnalysisResultRepository metricAnalysisResultRepository;

    private static final Set<MetricType> REQUIRED_METRIC_TYPES = EnumSet.of(
            MetricType.PRESSURE_BALANCE,
            MetricType.HALLUX_VALGUS,
            MetricType.ATHLETES_FOOT,
            MetricType.SKIN_IRRITATION,
            MetricType.FOOT_ENVIRONMENT
    );

    @Value("${openai.api-key:${OPENAI_API_KEY:}}")
    private String openAiApiKey;

    @Value("${openai.care-tips.model:" + DEFAULT_MODEL + "}")
    private String careTipsModel;

    @Transactional
    public void generateAndSaveIfNeeded(MeasurementSession measurementSession) {
        if (!StringUtils.hasText(openAiApiKey)) {
            log.warn("Care tips generation skipped. OPENAI_API_KEY is not configured. measurementSessionId={}",
                    measurementSession.getId());
            return;
        }

        try {
            DailyFootAnalysis analysis = dailyFootAnalysisRepository.findByMeasurementSessionId(measurementSession.getId())
                    .orElse(null);
            if (analysis == null) {
                log.warn("Care tips generation skipped. DailyFootAnalysis not found. measurementSessionId={}",
                        measurementSession.getId());
                return;
            }
            if (hasGeneratedCareTips(analysis)) {
                return;
            }

            String prompt = buildPrompt(measurementSession);
            List<String> generatedCareTips = requestCareTipsWithRetry(measurementSession.getId(), prompt);
            analysis.updateCareTips(generatedCareTips);
            dailyFootAnalysisRepository.save(analysis);
            log.info("Care tips generated and saved. measurementSessionId={}", measurementSession.getId());
        } catch (Exception e) {
            log.error("Care tips generation failed. measurementSessionId={}", measurementSession.getId(), e);
        }
    }

    private boolean hasGeneratedCareTips(DailyFootAnalysis analysis) {
        return analysis.getCareTips() != null
                && analysis.getCareTips().size() == 3
                && analysis.getCareTips().stream().allMatch(StringUtils::hasText);
    }

    private List<String> requestCareTipsWithRetry(Long measurementSessionId, String prompt) throws Exception {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_CARE_TIPS_REQUEST_ATTEMPTS; attempt++) {
            try {
                return requestCareTips(prompt);
            } catch (Exception e) {
                lastException = e;
                if (attempt < MAX_CARE_TIPS_REQUEST_ATTEMPTS) {
                    log.warn("Care tips generation request failed. Retrying once. measurementSessionId={}, attempt={}",
                            measurementSessionId, attempt, e);
                }
            }
        }
        throw new IllegalStateException("Care tips generation request failed.", lastException);
    }

    private List<String> requestCareTips(String prompt) throws Exception {
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
            throw new IllegalStateException("OpenAI response content is empty.");
        }

        return parseCareTips(content);
    }

    private Map<String, Object> buildOpenAiRequest(String prompt) {
        return Map.of(
                "model", StringUtils.hasText(careTipsModel) ? careTipsModel : DEFAULT_MODEL,
                "temperature", 0.4,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", """
                                        너는 발 건강 관리 앱의 비의료적 관리 팁 작성자다.
                                        질병을 진단하거나 확정하지 말고, 사용자가 바로 실천할 수 있는 생활 관리 문장만 작성한다.
                                        반드시 JSON 객체만 반환한다.
                                        """
                        ),
                        Map.of(
                                "role", "user",
                                "content", prompt
                        )
                )
        );
    }

    private String buildPrompt(MeasurementSession measurementSession) {
        List<MetricAnalysisResult> metricResults = findRequiredMetricResults(measurementSession.getId());
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
                저장된 5개 지표의 advice를 바탕으로 careTips 3개를 생성해.

                반환 형식:
                {
                  "careTips": [
                    "오른발 앞꿈치 스트레칭을 해주세요.",
                    "신발은 착용 후 충분히 말려주세요.",
                    "발볼이 좁은 신발은 피하는 것이 좋아요."
                  ]
                }

                작성 규칙:
                - careTips는 정확히 3개
                - 각 careTip은 한국어 30~70자 정도의 실천형 문장
                - careTips는 아래 5개 지표 advice 내용을 종합해서 중복 없이 작성
                - 과장, 진단, 치료 확정 표현 금지
                - 질환명 단정 대신 '경향', '참고', '관리' 표현 사용

                저장된 5개 지표 advice:
                """);

        appendLine(prompt, "measurementSessionId", measurementSession.getId());
        for (MetricAnalysisResult result : metricResults) {
            appendLine(prompt, "metricType", result.getMetricType());
            appendLine(prompt, "score", result.getScore());
            appendLine(prompt, "status", result.getStatus());
            appendLine(prompt, "advice", result.getAdvice());
        }

        return prompt.toString();
    }

    private List<MetricAnalysisResult> findRequiredMetricResults(Long measurementSessionId) {
        Report report = reportRepository.findByMeasurementSessionId(measurementSessionId)
                .orElseThrow(() -> new IllegalStateException("Report not found for care tips generation."));
        List<MetricAnalysisResult> results = metricAnalysisResultRepository.findByReportId(report.getId());
        Set<MetricType> savedMetricTypes = results.stream()
                .map(MetricAnalysisResult::getMetricType)
                .collect(Collectors.toSet());
        if (!savedMetricTypes.containsAll(REQUIRED_METRIC_TYPES)) {
            throw new IllegalStateException("Required metric results are missing for care tips generation.");
        }
        return results.stream()
                .filter(result -> REQUIRED_METRIC_TYPES.contains(result.getMetricType()))
                .toList();
    }

    private void appendLine(StringBuilder prompt, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && !StringUtils.hasText(text)) {
            return;
        }
        prompt.append("- ").append(key).append(": ").append(value).append('\n');
    }

    private List<String> parseCareTips(String content) throws Exception {
        JsonNode root = objectMapper.readTree(stripCodeFence(content));
        List<String> careTips = new ArrayList<>();
        JsonNode careTipsNode = root.path("careTips");
        if (careTipsNode.isArray()) {
            careTipsNode.forEach(node -> {
                String text = node.asText(null);
                if (StringUtils.hasText(text)) {
                    careTips.add(text.trim());
                }
            });
        }

        if (careTips.size() != 3) {
            throw new IllegalStateException("OpenAI care tips response is invalid.");
        }
        return List.copyOf(careTips);
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
}
