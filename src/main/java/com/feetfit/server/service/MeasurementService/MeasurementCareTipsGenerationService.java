package com.feetfit.server.service.MeasurementService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feetfit.server.domain.DailyFootAnalysis;
import com.feetfit.server.domain.HalluxValgusAnalysis;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.MetricAnalysisResult;
import com.feetfit.server.domain.Report;
import com.feetfit.server.domain.TinaPedisAnalysis;
import com.feetfit.server.repository.DailyFootAnalysisRepository;
import com.feetfit.server.repository.HalluxValgusAnalysisRepository;
import com.feetfit.server.repository.MetricAnalysisResultRepository;
import com.feetfit.server.repository.ReportRepository;
import com.feetfit.server.repository.TinaPedisAnalysisRepository;
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
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeasurementCareTipsGenerationService {

    private static final String OPENAI_CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-4.1-mini";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final DailyFootAnalysisRepository dailyFootAnalysisRepository;
    private final HalluxValgusAnalysisRepository halluxValgusAnalysisRepository;
    private final TinaPedisAnalysisRepository tinaPedisAnalysisRepository;
    private final ReportRepository reportRepository;
    private final MetricAnalysisResultRepository metricAnalysisResultRepository;

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

            CareTipsGenerationResult generated = requestCareTips(measurementSession, analysis);
            analysis.updateCareTips(generated.careTips(), generated.typeText());
            dailyFootAnalysisRepository.save(analysis);
            log.info("Care tips generated and saved. measurementSessionId={}", measurementSession.getId());
        } catch (Exception e) {
            log.error("Care tips generation failed. measurementSessionId={}", measurementSession.getId(), e);
        }
    }

    private boolean hasGeneratedCareTips(DailyFootAnalysis analysis) {
        return analysis.getCareTips() != null
                && analysis.getCareTips().size() == 3
                && analysis.getCareTips().stream().allMatch(StringUtils::hasText)
                && StringUtils.hasText(analysis.getTypeText());
    }

    private CareTipsGenerationResult requestCareTips(
            MeasurementSession measurementSession,
            DailyFootAnalysis analysis) throws Exception {
        JsonNode response = webClient.post()
                .uri(OPENAI_CHAT_COMPLETIONS_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + openAiApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(buildOpenAiRequest(measurementSession, analysis))
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

    private Map<String, Object> buildOpenAiRequest(
            MeasurementSession measurementSession,
            DailyFootAnalysis analysis) {
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
                                "content", buildPrompt(measurementSession, analysis)
                        )
                )
        );
    }

    private String buildPrompt(MeasurementSession measurementSession, DailyFootAnalysis analysis) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
                아래 측정 데이터를 바탕으로 careTips 3개와 typeText 1개를 생성해.

                반환 형식:
                {
                  "careTips": ["문장1", "문장2", "문장3"],
                  "typeText": "문장"
                }

                작성 규칙:
                - careTips는 정확히 3개
                - 각 careTip은 한국어 30~70자 정도의 실천형 문장
                - typeText는 발 타입과 신발 선택 참고 문장 1개, 한국어 120자 이내
                - 과장, 진단, 치료 확정 표현 금지
                - 데이터가 부족하면 '경향'과 '참고' 표현 사용

                측정 데이터:
                """);

        appendLine(prompt, "measurementSessionId", measurementSession.getId());
        appendLine(prompt, "balanceScore", analysis.getBalanceScore());
        appendLine(prompt, "balanceComment", analysis.getBalanceComment());
        appendLine(prompt, "leftPressurePercent", analysis.getLeftPressurePercent());
        appendLine(prompt, "rightPressurePercent", analysis.getRightPressurePercent());
        appendLine(prompt, "plantarFootprintAnalysisText", analysis.getPlantarFootprintAnalysisText());
        appendLine(prompt, "measuredLeftFootSizeMm", analysis.getMeasuredLeftFootSizeMm());
        appendLine(prompt, "measuredRightFootSizeMm", analysis.getMeasuredRightFootSizeMm());
        appendLine(prompt, "leftFootWidthMm", analysis.getLeftFootWidthMm());
        appendLine(prompt, "rightFootWidthMm", analysis.getRightFootWidthMm());
        appendLine(prompt, "avgTemperatureCelsius", analysis.getAvgTemperatureCelsius());
        appendLine(prompt, "avgHumidityPercent", analysis.getAvgHumidityPercent());
        appendLine(prompt, "footOdourPpm", analysis.getFootOdourPpm());
        appendLine(prompt, "footOdourComment", analysis.getFootOdourComment());

        halluxValgusAnalysisRepository.findByMeasurementSessionId(measurementSession.getId())
                .ifPresent(hallux -> appendHalluxValgus(prompt, hallux));
        tinaPedisAnalysisRepository.findByMeasurementSessionId(measurementSession.getId())
                .ifPresent(tineaPedis -> appendTineaPedis(prompt, tineaPedis));
        appendMetricResults(prompt, measurementSession.getId());

        return prompt.toString();
    }

    private void appendHalluxValgus(StringBuilder prompt, HalluxValgusAnalysis analysis) {
        appendLine(prompt, "halluxLeftToeAngleDegree", analysis.getLeftToeAngleDegree());
        appendLine(prompt, "halluxRightToeAngleDegree", analysis.getRightToeAngleDegree());
        appendLine(prompt, "halluxRiskScore", analysis.getRiskScore());
        appendLine(prompt, "halluxScoreAnalysisText", analysis.getScoreAnalysisText());
    }

    private void appendTineaPedis(StringBuilder prompt, TinaPedisAnalysis analysis) {
        appendLine(prompt, "fungalSuspicionSafetyScore", analysis.getFungalSuspicionSafetyScore());
        appendLine(prompt, "skinReactionSafetyScore", analysis.getSkinReactionSafetyScore());
        appendLine(prompt, "fungalSuspicionSafetyDescription", analysis.getFungalSuspicionSafetyDescription());
        appendLine(prompt, "skinReactionSafetyDescription", analysis.getSkinReactionSafetyDescription());
        appendLine(prompt, "tineaPedisTotalScoreDescription", analysis.getTotalScoreDescription());
    }

    private void appendMetricResults(StringBuilder prompt, Long measurementSessionId) {
        reportRepository.findByMeasurementSessionId(measurementSessionId)
                .ifPresent(report -> {
                    appendLine(prompt, "reportTotalScore", report.getTotalScore());
                    List<MetricAnalysisResult> results = metricAnalysisResultRepository.findByReportId(report.getId());
                    for (MetricAnalysisResult result : results) {
                        appendLine(prompt, "metricType", result.getMetricType());
                        appendLine(prompt, "metricScore", result.getScore());
                        appendLine(prompt, "metricStatus", result.getStatus());
                        appendLine(prompt, "metricAdvice", result.getAdvice());
                    }
                });
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

    private CareTipsGenerationResult parseCareTips(String content) throws Exception {
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

        String typeText = root.path("typeText").asText(null);
        if (careTips.size() != 3 || !StringUtils.hasText(typeText)) {
            throw new IllegalStateException("OpenAI care tips response is invalid.");
        }
        return new CareTipsGenerationResult(List.copyOf(careTips), typeText.trim());
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

    private record CareTipsGenerationResult(List<String> careTips, String typeText) {
    }
}
