package com.feetfit.server.service.MeasurementService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class MeasurementHardwareClient {

    private final WebClient webClient;

    @Value("${hardware.measurement.start-url}")
    private String measurementStartUrl;

    @Value("${hardware.measurement.photo-capture-url}")
    private String photoCaptureUrl;

    @Value("${hardware.measurement.pressure-environment-measurement-url}")
    private String pressureEnvironmentMeasurementUrl;

    public void requestPhotoCapture(Long measurementSessionId, String authorizationHeader) {
        requestHardwareTask("photo-capture", photoCaptureUrl, measurementSessionId, authorizationHeader);
    }

    public void requestInitialEnvironmentMeasurement(Long measurementSessionId, String authorizationHeader) {
        requestHardwareTask(
                "measurement-start",
                measurementStartUrl,
                measurementSessionId,
                authorizationHeader
        );
    }

    public void requestPressureAndEnvironmentMeasurement(Long measurementSessionId, String authorizationHeader) {
        requestHardwareTask(
                "pressure-environment-measurement",
                pressureEnvironmentMeasurementUrl,
                measurementSessionId,
                authorizationHeader
        );
    }

    private void requestHardwareTask(String taskName, String requestUrl, Long measurementSessionId, String authorizationHeader) {
        webClient.post()
                .uri(requestUrl)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("measurementSessionId", measurementSessionId))
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(10))
                .doOnSuccess(response -> log.info(
                        "Hardware task request accepted. taskName={}, url={}, measurementSessionId={}, status={}",
                        taskName,
                        requestUrl,
                        measurementSessionId,
                        response.getStatusCode()
                ))
                .doOnError(WebClientResponseException.class, e -> log.error(
                        "Hardware task request failed. taskName={}, url={}, measurementSessionId={}, status={}, responseBody={}",
                        taskName,
                        requestUrl,
                        measurementSessionId,
                        e.getStatusCode(),
                        e.getResponseBodyAsString()
                ))
                .doOnError(e -> !(e instanceof WebClientResponseException), e -> log.error(
                        "Hardware task request failed. taskName={}, url={}, measurementSessionId={}",
                        taskName,
                        requestUrl,
                        measurementSessionId,
                        e
                ))
                .subscribe();
    }
}
