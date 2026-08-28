package com.feetfit.server.service.MeasurementService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
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

    @Value("${hardware.measurement.environment-measurement-url}")
    private String environmentMeasurementUrl;

    @Value("${hardware.measurement.pressure-measurement-url}")
    private String pressureMeasurementUrl;

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

    public void requestPressureMeasurement(Long measurementSessionId, String authorizationHeader) {
        requestHardwareTask(
                "pressure-measurement",
                pressureMeasurementUrl,
                measurementSessionId,
                authorizationHeader
        );
    }

    public void requestEnvironmentMeasurement(Long measurementSessionId, String authorizationHeader) {
        requestHardwareTask(
                "environment-measurement",
                environmentMeasurementUrl,
                measurementSessionId,
                authorizationHeader
        );
    }

    private void requestHardwareTask(String taskName, String requestUrl, Long measurementSessionId, String authorizationHeader) {
        boolean hasAuthorization = StringUtils.hasText(authorizationHeader);
        String authorizationPreview = hasAuthorization
                ? authorizationHeader.substring(0, Math.min(authorizationHeader.length(), 12))
                : null;
        Map<String, Object> body = Map.of("measurementSessionId", measurementSessionId);

        log.info("Hardware task request sending. taskName={}, url={}, measurementSessionId={}, hasAuthorization={}, authorizationPreview={}, body={}",
                taskName,
                requestUrl,
                measurementSessionId,
                hasAuthorization,
                authorizationPreview,
                body
        );

        WebClient.RequestBodySpec requestSpec = webClient.post()
                .uri(requestUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON);

        if (hasAuthorization) {
            requestSpec.header(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }

        try {
            ResponseEntity<Void> response = requestSpec
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(10));

            log.info(
                    "Hardware task request accepted. taskName={}, url={}, measurementSessionId={}, status={}",
                    taskName,
                    requestUrl,
                    measurementSessionId,
                    response != null ? response.getStatusCode() : null
            );
        } catch (WebClientResponseException e) {
            log.error(
                    "Hardware task request failed. taskName={}, url={}, measurementSessionId={}, status={}, responseBody={}",
                    taskName,
                    requestUrl,
                    measurementSessionId,
                    e.getStatusCode(),
                    e.getResponseBodyAsString()
            );
        } catch (Exception e) {
            log.error(
                    "Hardware task request failed. taskName={}, url={}, measurementSessionId={}",
                    taskName,
                    requestUrl,
                    measurementSessionId,
                    e
            );
        }
    }
}
