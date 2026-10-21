package com.feetfit.server.service.OAuthService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.AuthHandler;
import com.feetfit.server.converter.UserConverter;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.jwt.TokenProvider;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.web.dto.kakao.KakaoLoginResponseDTO;
import com.feetfit.server.web.dto.user.UserResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@Transactional
@RequiredArgsConstructor
public class KakaoLoginCommandServiceImpl implements KakaoLoginCommandService {

    private final WebClient webClient;
    private final TokenProvider tokenProvider;
    private final UserRepository userRepository;

    @Override
    public UserResponseDTO.UserLoginResponseDTO login(String accessToken) {
        KakaoLoginResponseDTO.KakaoUserInfo kakaoUserInfo = getUserInfo(accessToken);
        String socialId = getSocialId(kakaoUserInfo);
        String nickname = getNickname(kakaoUserInfo);

        User user = userRepository.findBySocialTypeAndSocialId(SocialType.KAKAO, socialId)
                .orElseGet(() -> userRepository.save(UserConverter.toKakaoUser(socialId, nickname)));

        String serviceAccessToken = tokenProvider.createAccessToken(user.getId());
        String serviceRefreshToken = tokenProvider.createRefreshToken(user.getId());

        return UserConverter.toUserLoginResponseDTO(
                user,
                serviceAccessToken,
                serviceRefreshToken,
                tokenProvider.getAccessTokenExpiresIn()
        );
    }

    private KakaoLoginResponseDTO.KakaoUserInfo getUserInfo(String accessToken) {
        try {
            return webClient.get()
                    .uri("https://kapi.kakao.com/v2/user/me")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            response -> Mono.error(new AuthHandler(ErrorStatus.KAKAO_LOGIN_FAILED)))
                    .bodyToMono(KakaoLoginResponseDTO.KakaoUserInfo.class)
                    .block();
        } catch (AuthHandler e) {
            throw e;
        } catch (Exception e) {
            throw new AuthHandler(ErrorStatus.KAKAO_LOGIN_FAILED);
        }
    }

    private String getSocialId(KakaoLoginResponseDTO.KakaoUserInfo kakaoUserInfo) {
        if (kakaoUserInfo == null || kakaoUserInfo.getId() == null) {
            throw new AuthHandler(ErrorStatus.KAKAO_LOGIN_FAILED);
        }
        return String.valueOf(kakaoUserInfo.getId());
    }

    private String getNickname(KakaoLoginResponseDTO.KakaoUserInfo kakaoUserInfo) {
        KakaoLoginResponseDTO.KakaoUserInfo.KakaoAccount.Profile profile = getProfile(kakaoUserInfo);
        return profile != null ? profile.getNickname() : null;
    }

    private KakaoLoginResponseDTO.KakaoUserInfo.KakaoAccount.Profile getProfile(
            KakaoLoginResponseDTO.KakaoUserInfo kakaoUserInfo
    ) {
        if (kakaoUserInfo == null || kakaoUserInfo.getKakaoAccount() == null) {
            return null;
        }
        return kakaoUserInfo.getKakaoAccount().getProfile();
    }
}
