package com.feetfit.server.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class InternalApiKeyInterceptor implements HandlerInterceptor {

    public static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final InternalApiKeyVerifier internalApiKeyVerifier;

    public InternalApiKeyInterceptor(@Value("${internal.api-key:}") String configuredApiKey) {
        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            throw new IllegalStateException(
                    "INTERNAL_API_KEY must be configured for internal Server APIs");
        }
        this.internalApiKeyVerifier = new InternalApiKeyVerifier(configuredApiKey);
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        internalApiKeyVerifier.verify(request.getHeader(INTERNAL_API_KEY_HEADER));
        return true;
    }
}
