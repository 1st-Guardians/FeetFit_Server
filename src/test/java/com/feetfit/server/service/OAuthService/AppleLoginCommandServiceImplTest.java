package com.feetfit.server.service.OAuthService;

import com.feetfit.server.domain.User;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.domain.enums.UserStatus;
import com.feetfit.server.jwt.TokenProvider;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.web.dto.apple.AppleTokenResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AppleLoginCommandServiceImplTest {

    @Mock private AppleTokenClient appleTokenClient;
    @Mock private AppleIdTokenVerifier appleIdTokenVerifier;
    @Mock private TokenProvider tokenProvider;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private AppleLoginCommandServiceImpl service;

    @Test
    void login_existingAppleUser_returnsServiceTokens() {
        AppleTokenResponseDTO tokenResponse = tokenResponse("apple-id-token");
        User existing = appleUser(7L, "apple-subject");
        given(appleTokenClient.exchangeAuthorizationCode("authorization-code")).willReturn(tokenResponse);
        given(appleIdTokenVerifier.verify("apple-id-token"))
                .willReturn(new AppleIdTokenVerifier.AppleUserIdentity("apple-subject", "user@example.com"));
        given(userRepository.findBySocialTypeAndSocialId(SocialType.APPLE, "apple-subject"))
                .willReturn(Optional.of(existing));
        given(tokenProvider.createAccessToken(7L)).willReturn("access-token");
        given(tokenProvider.createRefreshToken(7L)).willReturn("refresh-token");
        given(tokenProvider.getAccessTokenExpiresIn()).willReturn(1000L);

        var response = service.login("authorization-code");

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getRequiresProfileSetup()).isTrue();
        then(userRepository).should(never()).save(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    void login_newAppleUser_createsUserByVerifiedSubject() {
        given(appleTokenClient.exchangeAuthorizationCode("authorization-code"))
                .willReturn(tokenResponse("apple-id-token"));
        given(appleIdTokenVerifier.verify("apple-id-token"))
                .willReturn(new AppleIdTokenVerifier.AppleUserIdentity("new-subject", "new-user@example.com"));
        given(userRepository.findBySocialTypeAndSocialId(SocialType.APPLE, "new-subject"))
                .willReturn(Optional.empty());
        given(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
                .willAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    ReflectionTestUtils.setField(user, "id", 8L);
                    return user;
                });
        given(tokenProvider.createAccessToken(8L)).willReturn("access-token");
        given(tokenProvider.createRefreshToken(8L)).willReturn("refresh-token");
        given(tokenProvider.getAccessTokenExpiresIn()).willReturn(1000L);

        service.login("authorization-code");

        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        then(userRepository).should().save(captor.capture());
        assertThat(captor.getValue().getSocialType()).isEqualTo(SocialType.APPLE);
        assertThat(captor.getValue().getSocialId()).isEqualTo("new-subject");
        assertThat(captor.getValue().getNickname()).isEqualTo("new-user");
    }

    private static AppleTokenResponseDTO tokenResponse(String idToken) {
        AppleTokenResponseDTO response = new AppleTokenResponseDTO();
        ReflectionTestUtils.setField(response, "idToken", idToken);
        return response;
    }

    private static User appleUser(Long id, String socialId) {
        return User.builder()
                .id(id)
                .nickname("애플사용자")
                .socialId(socialId)
                .socialType(SocialType.APPLE)
                .status(UserStatus.ACTIVE)
                .build();
    }
}
