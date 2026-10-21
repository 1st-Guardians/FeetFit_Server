package com.feetfit.server.service.UserService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feetfit.server.apiPayload.exception.GeneralException;
import com.feetfit.server.domain.AccountCleanupTask;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.service.OAuthService.AppleIdTokenVerifier;
import com.feetfit.server.service.OAuthService.AppleTokenClient;
import com.feetfit.server.web.dto.apple.AppleTokenResponseDTO;
import com.feetfit.server.web.dto.user.UserRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SocialUnlinkClientTest {
    private final AppleTokenClient apple = mock(AppleTokenClient.class);
    private final AppleIdTokenVerifier verifier = mock(AppleIdTokenVerifier.class);
    private final AtomicReference<ClientRequest> sent = new AtomicReference<>();
    private ClientResponse reply;
    private SocialUnlinkClient social;

    @BeforeEach
    void setup() {
        var web = WebClient.builder().exchangeFunction(request -> {
            sent.set(request);
            return Mono.just(reply);
        }).build();
        social = new SocialUnlinkClient(web, apple, verifier);
        ReflectionTestUtils.setField(social, "kakaoAdminKey", "");
    }

    @Test
    void missingCredentialsStillAllowsLocalWithdrawalButRequiresManualUnlink() {
        assertThat(social.prepare(SocialType.KAKAO, "123", null).requiresManualUnlink()).isTrue();
        assertThat(social.prepare(SocialType.APPLE, "apple-user", null).requiresManualUnlink()).isTrue();
        assertThat(sent.get()).isNull();
        verifyNoInteractions(apple, verifier);
    }

    @Test
    void kakaoTokenMustBelongToAuthenticatedMember() {
        reply = json(HttpStatus.OK, "{\"id\":123}");
        var body = new UserRequestDTO.WithdrawUserRequestDTO();
        ReflectionTestUtils.setField(body, "kakaoAccessToken", "provider-token");
        var prepared = social.prepare(SocialType.KAKAO, "123", body);
        assertThat(prepared.kind()).isEqualTo(AccountCleanupTask.Kind.KAKAO_UNLINK);
        assertThat(sent.get().url().toString()).isEqualTo("https://kapi.kakao.com/v2/user/me");
        assertThat(sent.get().headers().getFirst("Authorization")).isEqualTo("Bearer provider-token");
        reply = json(HttpStatus.OK, "{\"id\":456}");
        assertThatThrownBy(() -> social.prepare(SocialType.KAKAO, "123", body)).isInstanceOf(GeneralException.class);
    }

    @Test
    void kakaoAdminUnlinkUsesServerKeyAndAcceptsExplicitAlreadyUnlinkedResultOnly() {
        ReflectionTestUtils.setField(social, "kakaoAdminKey", "server-secret");
        var prepared = social.prepare(SocialType.KAKAO, "123", null);
        assertThat(prepared.requiresManualUnlink()).isFalse();
        assertThat(prepared.payload().token()).isNull();
        reply = json(HttpStatus.OK, "{\"id\":123}");
        social.unlinkKakao(prepared.payload());
        assertThat(sent.get().url().toString()).isEqualTo("https://kapi.kakao.com/v1/user/unlink");
        assertThat(sent.get().headers().getFirst("Authorization")).isEqualTo("KakaoAK server-secret");
        reply = json(HttpStatus.BAD_REQUEST, "{\"code\":-101}");
        assertThatCode(() -> social.unlinkKakao(prepared.payload())).doesNotThrowAnyException();
        reply = json(HttpStatus.UNAUTHORIZED, "{\"code\":-401}");
        assertThatThrownBy(() -> social.unlinkKakao(prepared.payload())).isInstanceOf(IllegalStateException.class);
        reply = json(HttpStatus.BAD_REQUEST, "{\"code\":-2}");
        assertThatThrownBy(() -> social.unlinkKakao(prepared.payload())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void appleReauthUsesVerifiedSubjectAndPrefersRefreshTokenForRevocation() throws Exception {
        var body = new UserRequestDTO.WithdrawUserRequestDTO();
        ReflectionTestUtils.setField(body, "appleAuthorizationCode", "fresh-code");
        var token = new ObjectMapper().readValue("{\"id_token\":\"signed\",\"refresh_token\":\"refresh\",\"access_token\":\"access\"}",
                AppleTokenResponseDTO.class);
        when(apple.exchangeAuthorizationCode("fresh-code")).thenReturn(token);
        when(verifier.verify("signed")).thenReturn(new AppleIdTokenVerifier.AppleUserIdentity("apple-user", null));
        var prepared = social.prepare(SocialType.APPLE, "apple-user", body);
        assertThat(prepared.kind()).isEqualTo(AccountCleanupTask.Kind.APPLE_REVOKE);
        assertThat(prepared.payload().token()).isEqualTo("refresh");
        social.revokeApple(prepared.payload().token());
        verify(apple).revoke("refresh");
        when(verifier.verify("signed")).thenReturn(new AppleIdTokenVerifier.AppleUserIdentity("other-user", null));
        assertThatThrownBy(() -> social.prepare(SocialType.APPLE, "apple-user", body)).isInstanceOf(GeneralException.class);
    }

    private ClientResponse json(HttpStatus status, String body) {
        return ClientResponse.create(status).header("Content-Type", "application/json").body(body).build();
    }
}
