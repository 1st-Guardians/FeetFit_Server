package com.feetfit.server.config;

import com.feetfit.server.service.ShoeService.ShoeRecommendationAiClient;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

class InternalApiKeyPropertyBindingTest {

    @Test
    void interceptorReadsCanonicalInternalApiKeyProperty() {
        try (AnnotationConfigApplicationContext context = contextWithCanonicalKey()) {
            InternalApiKeyInterceptor interceptor =
                    context.getBean(InternalApiKeyInterceptor.class);
            MockHttpServletRequest request = new MockHttpServletRequest(
                    "GET", "/api/internal/shoe-analysis/recommendation-context");
            request.addHeader(
                    InternalApiKeyInterceptor.INTERNAL_API_KEY_HEADER,
                    "yaml-resolved-secret");

            assertThat(interceptor.preHandle(
                    request, new MockHttpServletResponse(), new Object())).isTrue();
        }
    }

    @Test
    void aiClientReadsSameCanonicalInternalApiKeyProperty() {
        try (AnnotationConfigApplicationContext context = contextWithCanonicalKey()) {
            ShoeRecommendationAiClient client =
                    context.getBean(ShoeRecommendationAiClient.class);

            assertThat(ReflectionTestUtils.getField(client, "internalApiKey"))
                    .isEqualTo("yaml-resolved-secret");
        }
    }

    private static AnnotationConfigApplicationContext contextWithCanonicalKey() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                context,
                "internal.api-key=${INTERNAL_API_KEY:yaml-resolved-secret}",
                "ai.shoe-recommendation.enabled=false");
        context.registerBean(WebClient.class, () -> WebClient.builder().build());
        context.register(InternalApiKeyInterceptor.class, ShoeRecommendationAiClient.class);
        context.refresh();
        return context;
    }
}
