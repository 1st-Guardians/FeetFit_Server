package com.feetfit.server.config;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.GeneralException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class InternalApiKeyVerifier {

    private final String configuredApiKey;

    public InternalApiKeyVerifier(String configuredApiKey) {
        this.configuredApiKey = configuredApiKey;
    }

    public void verify(String suppliedApiKey) {
        if (configuredApiKey == null || configuredApiKey.isBlank()
                || suppliedApiKey == null || suppliedApiKey.isBlank()
                || !MessageDigest.isEqual(
                        configuredApiKey.getBytes(StandardCharsets.UTF_8),
                        suppliedApiKey.getBytes(StandardCharsets.UTF_8))) {
            throw new GeneralException(ErrorStatus._UNAUTHORIZED, "유효한 내부 API 키가 필요합니다.");
        }
    }
}
