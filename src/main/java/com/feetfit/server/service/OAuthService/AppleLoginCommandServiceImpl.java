package com.feetfit.server.service.OAuthService;

import com.feetfit.server.converter.UserConverter;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.jwt.TokenProvider;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.web.dto.apple.AppleTokenResponseDTO;
import com.feetfit.server.web.dto.user.UserResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class AppleLoginCommandServiceImpl implements AppleLoginCommandService {

    private final AppleTokenClient appleTokenClient;
    private final AppleIdTokenVerifier appleIdTokenVerifier;
    private final TokenProvider tokenProvider;
    private final UserRepository userRepository;

    @Override
    public UserResponseDTO.UserLoginResponseDTO login(String authorizationCode) {
        AppleTokenResponseDTO appleTokens =
                appleTokenClient.exchangeAuthorizationCode(authorizationCode);
        AppleIdTokenVerifier.AppleUserIdentity identity =
                appleIdTokenVerifier.verify(appleTokens.getIdToken());

        User user = userRepository
                .findBySocialTypeAndSocialId(SocialType.APPLE, identity.subject())
                .orElseGet(() -> userRepository.save(
                        UserConverter.toAppleUser(identity.subject(), identity.email())));

        String serviceAccessToken = tokenProvider.createAccessToken(user.getId());
        String serviceRefreshToken = tokenProvider.createRefreshToken(user.getId());

        return UserConverter.toUserLoginResponseDTO(
                user,
                serviceAccessToken,
                serviceRefreshToken,
                tokenProvider.getAccessTokenExpiresIn());
    }
}
