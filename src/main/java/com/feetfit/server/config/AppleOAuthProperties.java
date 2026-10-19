package com.feetfit.server.config;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.AuthHandler;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Getter
@Component
public class AppleOAuthProperties {

    @Value("${apple.oauth.client-id:${APPLE_CLIENT_ID:}}")
    private String clientId;

    @Value("${apple.oauth.team-id:${APPLE_TEAM_ID:}}")
    private String teamId;

    @Value("${apple.oauth.key-id:${APPLE_KEY_ID:}}")
    private String keyId;

    @Value("${apple.oauth.private-key-path:${APPLE_PRIVATE_KEY_PATH:}}")
    private String privateKeyPath;

    @Value("${apple.oauth.private-key-base64:${APPLE_PRIVATE_KEY_BASE64:}}")
    private String privateKeyBase64;

    public void validate() {
        if (!StringUtils.hasText(clientId)
                || !StringUtils.hasText(teamId)
                || !StringUtils.hasText(keyId)
                || (!StringUtils.hasText(privateKeyPath)
                && !StringUtils.hasText(privateKeyBase64))) {
            throw new AuthHandler(ErrorStatus.APPLE_CONFIGURATION_ERROR);
        }
    }
}
