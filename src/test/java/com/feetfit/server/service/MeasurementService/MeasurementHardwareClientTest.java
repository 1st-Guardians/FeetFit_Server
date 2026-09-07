package com.feetfit.server.service.MeasurementService;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
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

import static org.assertj.core.api.Assertions.assertThat;

class MeasurementHardwareClientTest {
    private static final String PHOTO_URL = "https://hardware.example/measurement/photo/start";
    private final AtomicReference<ClientRequest> sentRequest = new AtomicReference<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MeasurementHardwareClient client;

    @BeforeEach
    void setUp() {
        client = new MeasurementHardwareClient(WebClient.builder().exchangeFunction(request -> {
            sentRequest.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.ACCEPTED).build());
        }).build());
        ReflectionTestUtils.setField(client, "photoCaptureUrl", PHOTO_URL);
    }

    @Test
    void initialCaptureKeepsExistingPayload() throws Exception {
        client.requestPhotoCapture(79L, "Bearer test");

        assertPhotoRequest();
        assertThat(objectMapper.readTree(serializedBody(sentRequest.get())))
                .isEqualTo(objectMapper.readTree("{\"measurementSessionId\":79}"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void recaptureUsesSameEndpointAndSessionWithCurrentAttempt(int attempt) throws Exception {
        client.requestPhotoCapture(79L, "Bearer test", attempt);

        assertPhotoRequest();
        assertThat(objectMapper.readTree(serializedBody(sentRequest.get())))
                .isEqualTo(objectMapper.readTree("{\"measurementSessionId\":79,\"photoCaptureAttempt\":" + attempt + "}"));
    }

    private void assertPhotoRequest() {
        ClientRequest request = sentRequest.get();
        assertThat(request).isNotNull();
        assertThat(request.method()).isEqualTo(HttpMethod.POST);
        assertThat(request.url().toString()).isEqualTo(PHOTO_URL);
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
