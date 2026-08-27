package com.feetfit.server.service.ShoeService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.web.dto.report.FootTypeTextAiDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShoeRecommendationAiClientTest {

    private static final WebClient WEB_CLIENT = WebClient.builder().build();
    private static final String BATCH_URL =
            "https://ai.internal.example/api/reports/shoe-recommendations";
    private static final String SUMMARY_URL =
            "https://ai.internal.example/api/shoes/summaries";
    private static final String FOOT_TYPE_TEXT_URL =
            "https://foot-type-ai.internal.example/api/reports/foot-type-text";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void enabledAutomationRequiresInternalKey() {
        assertThatThrownBy(() -> client(BATCH_URL, SUMMARY_URL, 30, " ", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INTERNAL_API_KEY");
    }

    @Test
    void enabledAutomationRequiresBothAbsoluteUrls() {
        assertThatThrownBy(() -> client("", SUMMARY_URL, 30, "key", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ai.shoe-recommendation.url");
        assertThatThrownBy(() -> client(BATCH_URL, "relative/path", 30, "key", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ai.shoe-recommendation.summary-url");
    }

    @Test
    void configuredFootTypeEndpointMustBeAnAbsoluteHttpUrl() {
        assertThatThrownBy(() -> client(
                BATCH_URL, SUMMARY_URL, "relative/foot-type-text", 30, "key", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ai.foot-type-text.url");
    }

    @Test
    void timeoutMustBePositive() {
        assertThatThrownBy(() -> client(BATCH_URL, SUMMARY_URL, 0, "key", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout-seconds");
    }

    @Test
    void disabledAutomationCanStartWithoutSecretOrUrls() {
        ShoeRecommendationAiClient client = client("", "", 30, "", false);

        assertThat(client.isEnabled()).isFalse();
    }

    @Test
    void batchRequestCarriesBearerAndInternalCredentials() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build());
                })
                .build();
        ShoeRecommendationAiClient client = client(
                webClient, BATCH_URL, SUMMARY_URL, 30, "internal-key", true);

        client.requestAllShoeRecommendations(21L, "service-jwt");

        assertThat(captured.get().url().toString()).isEqualTo(BATCH_URL);
        assertThat(captured.get().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer service-jwt");
        assertThat(captured.get().headers().getFirst(
                ShoeRecommendationAiClient.INTERNAL_API_KEY_HEADER))
                .isEqualTo("internal-key");
    }

    @Test
    void footTypeRequestUsesSiblingEndpointCredentialsExactBodyAndResponse() throws Exception {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "application/json")
                            .body("""
                                    {
                                      "measurementSessionId": 21,
                                      "factsHash": "%s",
                                      "typeText": "오른발에 압력이 조금 더 실리는 편이에요.",
                                      "evidenceId": "PRESSURE_RIGHT_DOMINANT",
                                      "source": "OPENAI"
                                    }
                                    """.formatted("a".repeat(64)))
                            .build());
                })
                .build();
        ShoeRecommendationAiClient client = client(
                webClient, BATCH_URL, SUMMARY_URL, 30, "internal-key", true);
        FootTypeTextAiDTO.Request request = new FootTypeTextAiDTO.Request(
                21L,
                MeasurementStatus.COMPLETED,
                "a".repeat(64),
                new FootTypeTextAiDTO.Analysis(
                        253.0f, 248.0f, 85.0f, 70.0f,
                        46.0f, 54.0f,
                        "왼발 뒤꿈치와 오른발 앞꿈치에 압력이 집중되어 있습니다."
                )
        );

        FootTypeTextAiDTO.Response response =
                client.requestFootTypeText(request, "service-jwt");

        assertThat(captured.get().method()).isEqualTo(HttpMethod.POST);
        assertThat(captured.get().url().toString())
                .isEqualTo("https://ai.internal.example/api/reports/foot-type-text");
        assertThat(captured.get().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer service-jwt");
        assertThat(captured.get().headers().getFirst(
                ShoeRecommendationAiClient.INTERNAL_API_KEY_HEADER))
                .isEqualTo("internal-key");
        assertThat(captured.get().headers().getContentType().toString())
                .isEqualTo("application/json");

        JsonNode body = OBJECT_MAPPER.readTree(serializedBody(captured.get()));
        assertThat(body.path("measurementSessionId").asLong()).isEqualTo(21L);
        assertThat(body.path("measurementStatus").asText()).isEqualTo("COMPLETED");
        assertThat(body.path("factsHash").asText()).isEqualTo("a".repeat(64));
        assertThat(body.path("analysis").path("leftPressurePercent").floatValue())
                .isEqualTo(46.0f);
        assertThat(body.path("analysis").path("rightPressurePercent").floatValue())
                .isEqualTo(54.0f);
        assertThat(body.path("analysis").has("careTips")).isFalse();

        assertThat(response.measurementSessionId()).isEqualTo(21L);
        assertThat(response.factsHash()).isEqualTo("a".repeat(64));
        assertThat(response.typeText())
                .isEqualTo("오른발에 압력이 조금 더 실리는 편이에요.");
        assertThat(response.evidenceId()).isEqualTo("PRESSURE_RIGHT_DOMINANT");
        assertThat(response.source()).isEqualTo("OPENAI");
    }

    @Test
    void footTypeRequestUsesExplicitEndpointWhenConfigured() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, "application/json")
                            .body("""
                                    {
                                      "measurementSessionId": 21,
                                      "factsHash": "%s",
                                      "typeText": "아치를 안정적으로 받쳐주는 신발이 편안할 수 있어요.",
                                      "evidenceId": "ARCH_LOW",
                                      "source": "OPENAI"
                                    }
                                    """.formatted("a".repeat(64)))
                            .build());
                })
                .build();
        ShoeRecommendationAiClient client = client(
                webClient,
                BATCH_URL,
                SUMMARY_URL,
                FOOT_TYPE_TEXT_URL,
                30,
                "internal-key",
                true);
        FootTypeTextAiDTO.Request request = new FootTypeTextAiDTO.Request(
                21L,
                MeasurementStatus.COMPLETED,
                "a".repeat(64),
                new FootTypeTextAiDTO.Analysis(
                        253.0f, 248.0f, 85.0f, 70.0f,
                        46.0f, 54.0f,
                        "발바닥 중앙부 접촉 면적이 넓게 나타납니다."
                )
        );

        client.requestFootTypeText(request, "service-jwt");

        assertThat(captured.get().url().toString()).isEqualTo(FOOT_TYPE_TEXT_URL);
    }

    @Test
    void nonSuccessResponseIsNotSilentlyRetriedOrAccepted() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(
                        ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                                .body("AI busy")
                                .build()))
                .build();
        ShoeRecommendationAiClient client = client(
                webClient, BATCH_URL, SUMMARY_URL, 30, "internal-key", true);

        assertThatThrownBy(() -> client.requestAllShoeRecommendations(21L, "jwt"))
                .isInstanceOf(WebClientResponseException.ServiceUnavailable.class);
    }

    @Test
    void requestHonorsConfiguredOuterTimeout() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.never())
                .build();
        ShoeRecommendationAiClient client = client(
                webClient, BATCH_URL, SUMMARY_URL, 1, "internal-key", true);

        assertThatThrownBy(() -> client.requestAllShoeRecommendations(21L, "jwt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Timeout");
    }

    private static ShoeRecommendationAiClient client(
            String batchUrl,
            String summaryUrl,
            long timeoutSeconds,
            String internalKey,
            boolean enabled) {
        return new ShoeRecommendationAiClient(
                WEB_CLIENT, batchUrl, summaryUrl, "", timeoutSeconds, internalKey, enabled);
    }

    private static ShoeRecommendationAiClient client(
            String batchUrl,
            String summaryUrl,
            String footTypeTextUrl,
            long timeoutSeconds,
            String internalKey,
            boolean enabled) {
        return new ShoeRecommendationAiClient(
                WEB_CLIENT,
                batchUrl,
                summaryUrl,
                footTypeTextUrl,
                timeoutSeconds,
                internalKey,
                enabled);
    }

    private static ShoeRecommendationAiClient client(
            WebClient webClient,
            String batchUrl,
            String summaryUrl,
            long timeoutSeconds,
            String internalKey,
            boolean enabled) {
        return new ShoeRecommendationAiClient(
                webClient, batchUrl, summaryUrl, "", timeoutSeconds, internalKey, enabled);
    }

    private static ShoeRecommendationAiClient client(
            WebClient webClient,
            String batchUrl,
            String summaryUrl,
            String footTypeTextUrl,
            long timeoutSeconds,
            String internalKey,
            boolean enabled) {
        return new ShoeRecommendationAiClient(
                webClient,
                batchUrl,
                summaryUrl,
                footTypeTextUrl,
                timeoutSeconds,
                internalKey,
                enabled);
    }

    private static String serializedBody(ClientRequest request) {
        MockClientHttpRequest output = new MockClientHttpRequest(
                request.method(), request.url());
        BodyInserter.Context context = new BodyInserter.Context() {
            @Override
            public List<org.springframework.http.codec.HttpMessageWriter<?>> messageWriters() {
                return ExchangeStrategies.withDefaults().messageWriters();
            }

            @Override
            public Optional<org.springframework.http.server.reactive.ServerHttpRequest>
                    serverRequest() {
                return Optional.empty();
            }

            @Override
            public Map<String, Object> hints() {
                return Map.of();
            }
        };
        request.body().insert(output, context).block();
        return output.getBodyAsString().block();
    }
}
