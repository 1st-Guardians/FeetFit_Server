package com.feetfit.server.service.OAuthService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.AuthHandler;
import com.feetfit.server.config.AppleOAuthProperties;
import com.feetfit.server.web.dto.apple.AppleTokenResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class AppleTokenClient {

    private static final String APPLE_TOKEN_URL = "https://appleid.apple.com/auth/token";

    private final WebClient webClient;
    private final AppleOAuthProperties properties;
    private final AppleClientSecretGenerator clientSecretGenerator;

    public AppleTokenResponseDTO exchangeAuthorizationCode(String authorizationCode) {
        properties.validate();
        try {
            AppleTokenResponseDTO response = webClient.post()
                    .uri(APPLE_TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromFormData("client_id", properties.getClientId())
                            .with("client_secret", clientSecretGenerator.generate())
                            .with("code", authorizationCode)
                            .with("grant_type", "authorization_code"))
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            ignored -> Mono.error(new AuthHandler(ErrorStatus.APPLE_LOGIN_FAILED)))
                    .bodyToMono(AppleTokenResponseDTO.class)
                    .block(Duration.ofSeconds(15));

            if (response == null || !StringUtils.hasText(response.getIdToken())) {
                throw new AuthHandler(ErrorStatus.APPLE_LOGIN_FAILED);
            }
            return response;
        } catch (AuthHandler exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AuthHandler(ErrorStatus.APPLE_LOGIN_FAILED);
        }
    }
}
