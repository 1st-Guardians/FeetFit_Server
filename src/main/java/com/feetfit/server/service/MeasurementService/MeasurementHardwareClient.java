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

    public void requestMeasurementStart(Long measurementSessionId, String authorizationHeader) {
        webClient.post()
                .uri(measurementStartUrl)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("measurementSessionId", measurementSessionId))
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(10))
                .doOnSuccess(response -> log.info(
                        "Hardware measurement request accepted. url={}, measurementSessionId={}, status={}",
                        measurementStartUrl,
                        measurementSessionId,
                        response.getStatusCode()
                ))
                .doOnError(WebClientResponseException.class, e -> log.error(
                        "Hardware measurement request failed. url={}, measurementSessionId={}, status={}, responseBody={}",
                        measurementStartUrl,
                        measurementSessionId,
                        e.getStatusCode(),
                        e.getResponseBodyAsString()
                ))
                .doOnError(e -> !(e instanceof WebClientResponseException), e -> log.error(
                        "Hardware measurement request failed. url={}, measurementSessionId={}",
                        measurementStartUrl,
                        measurementSessionId,
                        e
                ))
                .subscribe();
    }
}
