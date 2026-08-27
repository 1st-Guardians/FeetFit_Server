package com.feetfit.server.config;

import com.feetfit.server.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalApiKeyInterceptorTest {

    @Test
    void missingEnvironmentSecretFailsFast() {
        assertThatThrownBy(() -> new InternalApiKeyInterceptor(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INTERNAL_API_KEY");
    }

    @Test
    void rejectsMissingServiceCredentialBeforeControllerInvocation() {
        InternalApiKeyInterceptor interceptor = new InternalApiKeyInterceptor("service-secret");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/shoes/recommendations");

        assertThatThrownBy(() -> interceptor.preHandle(
                request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void acceptsMatchingServiceCredential() {
        InternalApiKeyInterceptor interceptor = new InternalApiKeyInterceptor("service-secret");
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/internal/shoe-analysis/recommendation-context");
        request.addHeader(InternalApiKeyInterceptor.INTERNAL_API_KEY_HEADER, "service-secret");

        assertThat(interceptor.preHandle(
                request, new MockHttpServletResponse(), new Object())).isTrue();
    }
}
