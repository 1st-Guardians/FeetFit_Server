package com.feetfit.server.service.ShoeService;

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
public class ShoeAiClient {

    private final WebClient webClient;

    @Value("${ai.shoe-summary.request-url}")
    private String shoeSummaryRequestUrl;

    public void requestShoeSummaryGeneration(Long shoeId, String authorizationHeader) {
        webClient.post()
                .uri(shoeSummaryRequestUrl)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("shoeId", shoeId))
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(10))
                .doOnSuccess(response -> log.info(
                        "Shoe summary generation requested. url={}, shoeId={}, status={}",
                        shoeSummaryRequestUrl, shoeId, response.getStatusCode()
                ))
                .doOnError(WebClientResponseException.class, e -> log.error(
                        "Shoe summary generation request failed. url={}, shoeId={}, status={}, responseBody={}",
                        shoeSummaryRequestUrl, shoeId, e.getStatusCode(), e.getResponseBodyAsString()
                ))
                .doOnError(e -> !(e instanceof WebClientResponseException), e -> log.error(
                        "Shoe summary generation request failed. url={}, shoeId={}",
                        shoeSummaryRequestUrl, shoeId, e
                ))
                .subscribe();
    }
}
