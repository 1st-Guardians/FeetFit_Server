package com.feetfit.server.service.DrugService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class DrugRecommendationSummaryClientTest {
    private static final String VALID_SUMMARY = "무좀 관련 관리가 우선 필요해 보여 무좀약 정보를 먼저 추천해요. 약 사용 전에는 약사와 상담해 주세요.";
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<ClientRequest> requests = new ArrayList<>();

    @Test
    void usesConfiguredReasoningModelAndStrictJsonWithoutUnsupportedTemperature() throws Exception {
        var client = client(HttpStatus.OK, response(VALID_SUMMARY));
        var basis = new DrugRecommendationPolicy().rank(80f, 35f, 18f, 12f);

        assertThat(client.generate(basis)).isEqualTo(VALID_SUMMARY);
        assertThat(requests).hasSize(1);
        var request = requests.get(0);
        assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test-key");
        assertThat(request.url().getPath()).isEqualTo("/v1/chat/completions");
        JsonNode body = body(request);
        assertThat(body.path("model").asText()).isEqualTo(DrugRecommendationSummaryClient.DEFAULT_MODEL);
        assertThat(body.path("reasoning_effort").asText()).isEqualTo("low");
        assertThat(body.has("temperature")).isFalse();
        assertThat(body.path("response_format").path("json_schema").path("strict").asBoolean()).isTrue();
        assertThat(body.path("messages").path(1).path("content").asText())
                .contains("allHealthy", "ATHLETES_FOOT", "1~2문장", "30~120자", "needsCare", "구체적인 숫자")
                .doesNotContain("safetyScore", "priorityScore", "leftToeAngleDegree", "hvaLevel", "35.0", "18.0");
    }

    @Test
    void rejectsRefusalAndMalformedOrUnsafeOutput() throws Exception {
        var basis = new DrugRecommendationPolicy().rank(80f, 35f, 18f, 12f);
        for (String body : List.of("{\"choices\":[{\"message\":{\"refusal\":\"No\"}}]}",
                "{\"choices\":[]}", response(""), response("<script>" + VALID_SUMMARY + "</script>"), response("x".repeat(121)))) {
            requests.clear();
            var client = client(HttpStatus.OK, body);
            assertThatThrownBy(() -> client.generate(basis)).isInstanceOf(Exception.class);
            assertThat(requests).hasSize(body.contains("refusal") ? 1 : 5);
        }
    }

    @Test
    void missingKeyMakesNoExternalRequest() {
        var client = client(HttpStatus.OK, "{}");
        ReflectionTestUtils.setField(client, "apiKey", "");
        assertThatThrownBy(() -> client.generate(new DrugRecommendationPolicy().rank(null, null, null, null)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(requests).isEmpty();
    }

    @Test
    void propagatesApiFailureForStoredFallbackHandling() {
        var client = client(HttpStatus.TOO_MANY_REQUESTS, "{}");
        assertThatThrownBy(() -> client.generate(new DrugRecommendationPolicy().rank(80f, 35f, 18f, 12f)))
                .isInstanceOf(org.springframework.web.reactive.function.client.WebClientResponseException.class);
        assertThat(requests).hasSize(1);
    }

    @Test
    void malformedJsonIsCorrectedAndSuccessfulRetryStopsFurtherRequests() throws Exception {
        var client = client(HttpStatus.OK, "not-json", response(VALID_SUMMARY));

        assertThat(client.generate(new DrugRecommendationPolicy().rank(80f, 35f, 18f, 12f))).isEqualTo(VALID_SUMMARY);

        assertThat(requests).hasSize(2);
        assertThat(body(requests.get(1)).path("messages").path(1).path("content").asText())
                .contains("이전 응답의 형식 검증에 실패했습니다", "INVALID_JSON");
    }

    @Test
    void wrongFieldTypeExtraFieldsAndTrailingJsonAreRetriedAtMostFiveTimes() throws Exception {
        for (String content : List.of("{\"summary\":123}", "{\"summary\":\"text\",\"other\":true}",
                "{\"summary\":\"text\"} {}", "[]")) {
            requests.clear();
            String raw = mapper.writeValueAsString(Map.of("choices", List.of(Map.of("message", Map.of("content", content)))));
            var client = client(HttpStatus.OK, raw);

            assertThatThrownBy(() -> client.generate(new DrugRecommendationPolicy().rank(80f, 35f, 18f, 12f)))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(requests).hasSize(5);
        }
    }

    @Test
    void healthySummaryUsesApprovedCopyInPromptSchemaAndResponse() throws Exception {
        var client = client(HttpStatus.OK, response(DrugRecommendationPolicy.HEALTHY_SUMMARY));

        assertThat(client.generate(new DrugRecommendationPolicy().rank(100f, 100f, 15f, 5f)))
                .isEqualTo(DrugRecommendationPolicy.HEALTHY_SUMMARY);

        assertThat(requests).hasSize(1);
        JsonNode request = body(requests.get(0));
        assertThat(request.path("messages").path(1).path("content").asText())
                .contains(DrugRecommendationPolicy.HEALTHY_SUMMARY, "확정 요약 문장을 그대로");
        assertThat(request.path("response_format").path("json_schema").path("schema")
                .path("properties").path("summary").path("enum").get(0).asText())
                .isEqualTo(DrugRecommendationPolicy.HEALTHY_SUMMARY);
    }

    @Test
    void healthySummaryThatIgnoresApprovedWordingIsRetried() throws Exception {
        var client = client(HttpStatus.OK, response(VALID_SUMMARY), response(DrugRecommendationPolicy.HEALTHY_SUMMARY));

        assertThat(client.generate(new DrugRecommendationPolicy().rank(100f, 100f, 15f, 5f)))
                .isEqualTo(DrugRecommendationPolicy.HEALTHY_SUMMARY);

        assertThat(requests).hasSize(2);
        assertThat(body(requests.get(1)).path("messages").path(1).path("content").asText())
                .contains("APPROVED_HEALTHY_SUMMARY_REQUIRED");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "무좀 관련 안전점수가 35점으로 나타나 무좀약 정보를 먼저 추천해요. 약 사용 전에는 약사와 상담해 주세요.",
            "엄지발가락이 18도로 기울어져 발가락 보호밴드를 먼저 추천해요. 사용 전 전문가와 상담해 주세요.",
            "피부 관리 필요도가 80%로 나타나 상처 보호·발 관리 제품을 먼저 추천해요. 사용 전 제품 안내를 확인해 주세요.",
            "엄지발가락 상태가 진행 단계로 보여 발가락 보호밴드를 먼저 추천해요. 사용 전 전문가와 상담해 주세요.",
            "무좀 관련 안전점수가 낮아 무좀약 정보를 먼저 추천해요. 약 사용 전에는 약사와 상담해 주세요."
    })
    void numericOrTechnicalSummaryIsRetriedWithoutRepeatingMeasurements(String numericSummary) throws Exception {
        var client = client(HttpStatus.OK, response(numericSummary), response(VALID_SUMMARY));

        assertThat(client.generate(new DrugRecommendationPolicy().rank(80f, 35f, 18f, 12f)))
                .isEqualTo(VALID_SUMMARY);

        assertThat(requests).hasSize(2);
        assertThat(body(requests.get(1)).path("messages").path(1).path("content").asText())
                .contains("SIMPLE_SUMMARY_WITHOUT_NUMERIC_METRICS_REQUIRED")
                .doesNotContain(numericSummary);
    }

    @Test
    void longSummaryIsRetriedAndShortSummaryBelowOldMinimumIsAccepted() throws Exception {
        var client = client(HttpStatus.OK, response("긴".repeat(121)), response(VALID_SUMMARY));

        assertThat(VALID_SUMMARY.length()).isLessThan(80);
        assertThat(client.generate(new DrugRecommendationPolicy().rank(80f, 35f, 18f, 12f)))
                .isEqualTo(VALID_SUMMARY);

        assertThat(requests).hasSize(2);
        assertThat(body(requests.get(1)).path("messages").path(1).path("content").asText())
                .contains("SUMMARY_LENGTH_30_TO_120_REQUIRED");
    }

    private DrugRecommendationSummaryClient client(HttpStatus status, String... bodies) {
        var index = new java.util.concurrent.atomic.AtomicInteger();
        var webClient = WebClient.builder().exchangeFunction(request -> {
            requests.add(request);
            return Mono.just(ClientResponse.create(status).header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(bodies[Math.min(index.getAndIncrement(), bodies.length - 1)]).build());
        }).build();
        var client = new DrugRecommendationSummaryClient(webClient, mapper);
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        return client;
    }

    private String response(String summary) throws Exception {
        return mapper.writeValueAsString(Map.of("choices", List.of(Map.of("message", Map.of(
                "content", mapper.writeValueAsString(Map.of("summary", summary)))))));
    }

    private JsonNode body(ClientRequest request) throws Exception {
        var output = new MockClientHttpRequest(request.method(), request.url());
        request.body().insert(output, new BodyInserter.Context() {
            public List<org.springframework.http.codec.HttpMessageWriter<?>> messageWriters() {
                return ExchangeStrategies.withDefaults().messageWriters();
            }
            public Optional<org.springframework.http.server.reactive.ServerHttpRequest> serverRequest() {
                return Optional.empty();
            }
            public Map<String, Object> hints() {
                return Map.of();
            }
        }).block();
        return mapper.readTree(output.getBodyAsString().block());
    }
}
