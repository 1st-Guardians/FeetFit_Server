package com.feetfit.server.service.UserService;

import com.fasterxml.jackson.databind.JsonNode;
import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.GeneralException;
import com.feetfit.server.apiPayload.exception.handler.AuthHandler;
import com.feetfit.server.domain.AccountCleanupTask;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.service.OAuthService.AppleIdTokenVerifier;
import com.feetfit.server.service.OAuthService.AppleTokenClient;
import com.feetfit.server.web.dto.user.UserRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class SocialUnlinkClient {
    public record Prepared(AccountCleanupTask.Kind kind, AccountCleanupStore.Payload payload) {
        public boolean requiresManualUnlink() { return payload == null; }
    }

    private final WebClient webClient;
    private final AppleTokenClient appleTokens;
    private final AppleIdTokenVerifier appleVerifier;

    @Value("${kakao.admin-key:${KAKAO_ADMIN_KEY:}}")
    private String kakaoAdminKey;

    public Prepared prepare(SocialType type, String subject, UserRequestDTO.WithdrawUserRequestDTO request) {
        if (type == SocialType.APPLE) {
            if (request == null || !StringUtils.hasText(request.getAppleAuthorizationCode())) return new Prepared(null, null);
            var tokens = appleTokens.exchangeAuthorizationCode(request.getAppleAuthorizationCode());
            if (!subject.equals(appleVerifier.verify(tokens.getIdToken()).subject())) {
                throw new GeneralException(ErrorStatus._FORBIDDEN);
            }
            String token = StringUtils.hasText(tokens.getRefreshToken()) ? tokens.getRefreshToken() : tokens.getAccessToken();
            return StringUtils.hasText(token) ? new Prepared(AccountCleanupTask.Kind.APPLE_REVOKE,
                    new AccountCleanupStore.Payload(null, null, subject, token, false)) : new Prepared(null, null);
        }
        if (StringUtils.hasText(kakaoAdminKey)) {
            return new Prepared(AccountCleanupTask.Kind.KAKAO_UNLINK,
                    new AccountCleanupStore.Payload(null, null, subject, null, true));
        }
        if (request == null || !StringUtils.hasText(request.getKakaoAccessToken())) return new Prepared(null, null);
        JsonNode info;
        try {
            info = webClient.get().uri("https://kapi.kakao.com/v2/user/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + request.getKakaoAccessToken())
                    .retrieve().bodyToMono(JsonNode.class).timeout(Duration.ofSeconds(15)).block();
        } catch (RuntimeException exception) {
            throw new AuthHandler(ErrorStatus.KAKAO_LOGIN_FAILED);
        }
        if (info == null || !subject.equals(info.path("id").asText())) throw new GeneralException(ErrorStatus._FORBIDDEN);
        return new Prepared(AccountCleanupTask.Kind.KAKAO_UNLINK,
                new AccountCleanupStore.Payload(null, null, subject, request.getKakaoAccessToken(), false));
    }

    public void unlinkKakao(AccountCleanupStore.Payload payload) {
        var request = webClient.post().uri("https://kapi.kakao.com/v1/user/unlink");
        WebClient.RequestHeadersSpec<?> response;
        if (payload.adminKey()) {
            if (!StringUtils.hasText(kakaoAdminKey)) throw new IllegalStateException("Kakao unlink configuration is missing.");
            response = request.header(HttpHeaders.AUTHORIZATION, "KakaoAK " + kakaoAdminKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("target_id_type", "user_id").with("target_id", payload.subject()));
        } else {
            response = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + payload.token());
        }
        Boolean confirmed = response.exchangeToMono(reply -> {
            if (reply.statusCode().is2xxSuccessful()) return reply.bodyToMono(JsonNode.class)
                    .map(body -> payload.subject().equals(body.path("id").asText()));
            if (reply.statusCode().value() == 400) return reply.bodyToMono(JsonNode.class)
                    // Kakao -101 explicitly means this account is not linked to the application.
                    .map(body -> body.path("code").asInt() == -101);
            return reply.releaseBody().then(Mono.error(new IllegalStateException("Kakao unlink request failed.")));
        }).timeout(Duration.ofSeconds(15)).block();
        if (!Boolean.TRUE.equals(confirmed)) {
            throw new IllegalStateException("Kakao unlink response was not confirmed.");
        }
    }

    public void revokeApple(String token) {
        appleTokens.revoke(token);
    }
}
