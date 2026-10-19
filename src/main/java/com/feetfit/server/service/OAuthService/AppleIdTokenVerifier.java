package com.feetfit.server.service.OAuthService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.AuthHandler;
import com.feetfit.server.config.AppleOAuthProperties;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AppleIdTokenVerifier {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final String APPLE_JWK_SET_URI = "https://appleid.apple.com/auth/keys";

    private final AppleOAuthProperties properties;
    private final NimbusJwtDecoder jwtDecoder;

    public AppleIdTokenVerifier(AppleOAuthProperties properties) {
        this.properties = properties;
        this.jwtDecoder = NimbusJwtDecoder.withJwkSetUri(APPLE_JWK_SET_URI).build();

        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(APPLE_ISSUER);
        OAuth2TokenValidator<Jwt> audienceValidator = this::validateAudience;
        this.jwtDecoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
    }

    public AppleUserIdentity verify(String idToken) {
        properties.validate();
        try {
            Jwt jwt = jwtDecoder.decode(idToken);
            String subject = jwt.getSubject();
            if (!StringUtils.hasText(subject)) {
                throw new AuthHandler(ErrorStatus.APPLE_LOGIN_FAILED);
            }
            return new AppleUserIdentity(subject, jwt.getClaimAsString("email"));
        } catch (AuthHandler exception) {
            throw exception;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new AuthHandler(ErrorStatus.APPLE_LOGIN_FAILED);
        }
    }

    private OAuth2TokenValidatorResult validateAudience(Jwt jwt) {
        if (jwt.getAudience().contains(properties.getClientId())) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Apple ID token audience is invalid.", null));
    }

    public record AppleUserIdentity(String subject, String email) {
    }
}
