package com.feetfit.server.service.MeasurementService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feetfit.server.event.MeasurementHardwareFailureEvent;
import com.feetfit.server.domain.enums.MeasurementFailureReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MeasurementHardwareClientTest {
    private static final String START_URL = "https://hardware.example/measurement/start";
    private static final String PHOTO_URL = "https://hardware.example/measurement/photo/start";
    private static final String ENVIRONMENT_URL = "https://hardware.example/measurement/environment/start";
    private static final String PRESSURE_URL = "https://hardware.example/measurement/pressure/start";
    private final AtomicReference<ClientRequest> sentRequest = new AtomicReference<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MeasurementHardwareClient client;
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    @BeforeEach
    void setUp() {
        client = new MeasurementHardwareClient(WebClient.builder().exchangeFunction(request -> {
            sentRequest.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.ACCEPTED).build());
        }).build(), events);
        ReflectionTestUtils.setField(client, "measurementStartUrl", START_URL);
        ReflectionTestUtils.setField(client, "photoCaptureUrl", PHOTO_URL);
        ReflectionTestUtils.setField(client, "environmentMeasurementUrl", ENVIRONMENT_URL);
        ReflectionTestUtils.setField(client, "pressureMeasurementUrl", PRESSURE_URL);
    }

    @Test
    void measurementStartKeepsExistingPayload() throws Exception {
        client.requestMeasurementStart(79L, "Bearer test");

        assertHardwareRequest(START_URL);
        assertThat(objectMapper.readTree(serializedBody(sentRequest.get())))
                .isEqualTo(objectMapper.readTree("{\"measurementSessionId\":79}"));
    }

    @Test
    void photoCaptureKeepsExistingPayload() throws Exception {
        client.requestPhotoCapture(79L, "Bearer test");

        assertHardwareRequest(PHOTO_URL);
        assertThat(objectMapper.readTree(serializedBody(sentRequest.get())))
                .isEqualTo(objectMapper.readTree("{\"measurementSessionId\":79}"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void recaptureUsesPhotoEndpointAndSessionWithCurrentAttempt(int attempt) throws Exception {
        client.requestPhotoCapture(79L, "Bearer test", attempt);

        assertHardwareRequest(PHOTO_URL);
        assertThat(objectMapper.readTree(serializedBody(sentRequest.get())))
                .isEqualTo(objectMapper.readTree("{\"measurementSessionId\":79,\"photoCaptureAttempt\":" + attempt + "}"));
    }

    @Test
    void environmentMeasurementKeepsExistingPayload() throws Exception {
        client.requestEnvironmentMeasurement(79L, "Bearer test");

        assertHardwareRequest(ENVIRONMENT_URL);
        assertThat(objectMapper.readTree(serializedBody(sentRequest.get())))
                .isEqualTo(objectMapper.readTree("{\"measurementSessionId\":79}"));
    }

    @Test
    void initialPressureMeasurementUsesDefaultAttempt() throws Exception {
        client.requestPressureMeasurement(79L, "Bearer test");

        assertHardwareRequest(PRESSURE_URL);
        assertThat(objectMapper.readTree(serializedBody(sentRequest.get())))
                .isEqualTo(objectMapper.readTree("{\"measurementSessionId\":79,\"pressureMeasurementAttempt\":0}"));
    }

    @Test
    void pressureRetryUsesPressureEndpointAndSessionWithCurrentAttempt() throws Exception {
        client.requestPressureMeasurement(79L, "Bearer test", 2);

        assertHardwareRequest(PRESSURE_URL);
        assertThat(objectMapper.readTree(serializedBody(sentRequest.get())))
                .isEqualTo(objectMapper.readTree("{\"measurementSessionId\":79,\"pressureMeasurementAttempt\":2}"));
    }

    @Test
    void pressureHttpErrorRequestsRecoveryForTheSameAttempt() {
        ReflectionTestUtils.setField(client, "webClient", WebClient.builder().exchangeFunction(request ->
                Mono.just(ClientResponse.create(HttpStatus.BAD_GATEWAY).body("upstream error").build())).build());

        client.requestPressureMeasurement(79L, "Bearer test", 2);

        verify(events).publishEvent(new MeasurementHardwareFailureEvent(79L, 2, MeasurementFailureReason.NETWORK_ERROR));
    }

    @Test
    void pressureConnectionErrorRequestsRecovery() {
        ReflectionTestUtils.setField(client, "webClient", WebClient.builder().exchangeFunction(request ->
                Mono.error(new IllegalStateException("connection failed"))).build());

        client.requestPressureMeasurement(79L, "Bearer test", 0);

        verify(events).publishEvent(new MeasurementHardwareFailureEvent(79L, 0, MeasurementFailureReason.NETWORK_ERROR));
    }

    @Test
    void pressureTimeoutRetainsItsSpecificReason() {
        ReflectionTestUtils.setField(client, "webClient", WebClient.builder().exchangeFunction(request ->
                Mono.error(new IllegalStateException("timeout", new java.util.concurrent.TimeoutException()))).build());

        client.requestPressureMeasurement(79L, "Bearer test", 0);

        verify(events).publishEvent(new MeasurementHardwareFailureEvent(79L, 0, MeasurementFailureReason.HARDWARE_TIMEOUT));
    }

    @Test
    void pressureResponseWaitDefaultsToThirtySeconds() {
        assertThat(ReflectionTestUtils.getField(client, "pressureRequestTimeoutSeconds")).isEqualTo(30L);
    }

    @Test
    void configuredResponseTimeoutPublishesOneEventWithoutResendingMeasurement() {
        AtomicInteger calls = new AtomicInteger();
        ReflectionTestUtils.setField(client, "pressureRequestTimeoutSeconds", 1L);
        ReflectionTestUtils.setField(client, "webClient", WebClient.builder().exchangeFunction(request -> {
            calls.incrementAndGet();
            return Mono.never();
        }).build());

        client.requestPressureMeasurement(79L, "Bearer test", 2);

        assertThat(calls).hasValue(1);
        verify(events).publishEvent(new MeasurementHardwareFailureEvent(79L, 2, MeasurementFailureReason.HARDWARE_TIMEOUT));
        verifyNoMoreInteractions(events);
    }

    @Test
    void delayedAcceptedResponseDoesNotRequestRecovery() {
        ReflectionTestUtils.setField(client, "pressureRequestTimeoutSeconds", 1L);
        ReflectionTestUtils.setField(client, "webClient", WebClient.builder().exchangeFunction(request ->
                Mono.just(ClientResponse.create(HttpStatus.ACCEPTED).build())
                        .delayElement(java.time.Duration.ofMillis(100))).build());

        client.requestPressureMeasurement(79L, "Bearer test", 0);

        verifyNoInteractions(events);
    }

    @Test
    void photoErrorDoesNotIncorrectlyRequestPressureRetry() {
        ReflectionTestUtils.setField(client, "webClient", WebClient.builder().exchangeFunction(request ->
                Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build())).build());

        client.requestPhotoCapture(79L, "Bearer test");

        verifyNoInteractions(events);
    }

    private void assertHardwareRequest(String expectedUrl) {
        ClientRequest request = sentRequest.get();
        assertThat(request).isNotNull();
        assertThat(request.method()).isEqualTo(HttpMethod.POST);
        assertThat(request.url().toString()).isEqualTo(expectedUrl);
        assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test");
        assertThat(request.headers().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    private static String serializedBody(ClientRequest request) {
        MockClientHttpRequest output = new MockClientHttpRequest(request.method(), request.url());
        BodyInserter.Context context = new BodyInserter.Context() {
            @Override
            public List<HttpMessageWriter<?>> messageWriters() {
                return ExchangeStrategies.withDefaults().messageWriters();
            }

            @Override
            public Optional<ServerHttpRequest> serverRequest() {
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
