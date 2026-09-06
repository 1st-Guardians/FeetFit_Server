package com.feetfit.server.service.ShoeService;

import com.feetfit.server.web.dto.report.FootTypeTextAiDTO;
import com.feetfit.server.web.dto.shoe.ShoeRequestDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.net.URI;
import java.util.Map;

@Component
public class ShoeRecommendationAiClient {

    static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final WebClient webClient;
    private final String endpoint;
    private final String summaryEndpoint;
    private final String synchronousSummaryEndpoint;
    private final String footTypeTextEndpoint;
    private final Duration timeout;
    private final Duration synchronousSummaryTimeout;
    private final String internalApiKey;
    private final boolean enabled;

    public ShoeRecommendationAiClient(
            WebClient webClient,
            @Value("${ai.shoe-recommendation.url:}")
            String endpoint,
            @Value("${ai.shoe-recommendation.summary-url:}")
            String summaryEndpoint,
            @Value("${ai.foot-type-text.url:}")
            String configuredFootTypeTextEndpoint,
            @Value("${ai.shoe-recommendation.timeout-seconds:1200}") long timeoutSeconds,
            @Value("${ai.shoe-recommendation.summary-timeout-seconds:10}")
            long summaryTimeoutSeconds,
            @Value("${internal.api-key:}") String internalApiKey,
            @Value("${ai.shoe-recommendation.enabled:true}") boolean enabled) {
        if (enabled) {
            requireHttpUrl(endpoint, "ai.shoe-recommendation.url");
            requireHttpUrl(summaryEndpoint, "ai.shoe-recommendation.summary-url");
        } else {
            validateOptionalHttpUrl(endpoint, "ai.shoe-recommendation.url");
            validateOptionalHttpUrl(summaryEndpoint, "ai.shoe-recommendation.summary-url");
        }
        validateOptionalHttpUrl(configuredFootTypeTextEndpoint, "ai.foot-type-text.url");
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException(
                    "ai.shoe-recommendation.timeout-seconds must be positive");
        }
        if (summaryTimeoutSeconds <= 0 || summaryTimeoutSeconds > 10) {
            throw new IllegalArgumentException(
                    "ai.shoe-recommendation.summary-timeout-seconds must be between 1 and 10");
        }
        if (enabled && !StringUtils.hasText(internalApiKey)) {
            throw new IllegalStateException(
                    "INTERNAL_API_KEY must be configured when recommendation automation is enabled");
        }
        this.webClient = webClient;
        this.endpoint = endpoint;
        this.summaryEndpoint = summaryEndpoint;
        this.synchronousSummaryEndpoint = StringUtils.hasText(summaryEndpoint)
                ? childEndpoint(summaryEndpoint, "generate")
                : "";
        this.footTypeTextEndpoint = enabled
                ? resolveFootTypeTextEndpoint(endpoint, configuredFootTypeTextEndpoint)
                : "";
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        // A shoe detail request must remain bounded even though the batch endpoint is
        // intentionally allowed to run for much longer.
        this.synchronousSummaryTimeout = Duration.ofSeconds(summaryTimeoutSeconds);
        this.internalApiKey = internalApiKey;
        this.enabled = enabled;
    }

    public void requestAllShoeRecommendations(Long measurementSessionId, String accessToken) {
        requireEnabled();
        requireInternalApiKey();

        webClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(INTERNAL_API_KEY_HEADER, internalApiKey)
                .bodyValue(Map.of("measurementSessionId", measurementSessionId))
                .retrieve()
                .toBodilessEntity()
                .block(timeout);
    }

    public void requestShoeSummary(
            Long shoeId, Long measurementSessionId, String accessToken) {
        requireEnabled();
        requireInternalApiKey();

        webClient.post()
                .uri(summaryEndpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(INTERNAL_API_KEY_HEADER, internalApiKey)
                .bodyValue(Map.of(
                        "shoeId", shoeId,
                        "measurementSessionId", measurementSessionId
                ))
                .retrieve()
                .toBodilessEntity()
                .block(timeout);
    }

    public ShoeRequestDTO.SaveShoeSummariesDTO generateShoeSummary(
            Long shoeId, Long measurementSessionId, String accessToken) {
        requireEnabled();
        requireInternalApiKey();

        ShoeRequestDTO.SaveShoeSummariesDTO response = webClient.post()
                .uri(synchronousSummaryEndpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(INTERNAL_API_KEY_HEADER, internalApiKey)
                .bodyValue(Map.of(
                        "shoeId", shoeId,
                        "measurementSessionId", measurementSessionId
                ))
                .retrieve()
                .bodyToMono(ShoeRequestDTO.SaveShoeSummariesDTO.class)
                .block(synchronousSummaryTimeout);
        if (response == null) {
            throw new IllegalStateException("Feetfit_AI returned an empty shoe summary response");
        }
        return response;
    }

    public FootTypeTextAiDTO.Response requestFootTypeText(
            FootTypeTextAiDTO.Request request, String accessToken) {
        requireEnabled();
        requireInternalApiKey();

        FootTypeTextAiDTO.Response response = webClient.post()
                .uri(footTypeTextEndpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(INTERNAL_API_KEY_HEADER, internalApiKey)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(FootTypeTextAiDTO.Response.class)
                .block(timeout);
        if (response == null) {
            throw new IllegalStateException("Feetfit_AI returned an empty foot type response");
        }
        return response;
    }

    private void requireInternalApiKey() {
        if (!StringUtils.hasText(internalApiKey)) {
            throw new IllegalStateException(
                    "INTERNAL_API_KEY must be configured for recommendation automation");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException("shoe recommendation automation is disabled");
        }
    }

    private static void requireHttpUrl(String value, String propertyName) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(propertyName + " must be a valid URL", exception);
        }
        if (uri.getHost() == null
                || (!"http".equalsIgnoreCase(uri.getScheme())
                && !"https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException(propertyName + " must be an absolute HTTP(S) URL");
        }
    }

    private static void validateOptionalHttpUrl(String value, String propertyName) {
        if (StringUtils.hasText(value)) {
            requireHttpUrl(value, propertyName);
        }
    }

    private static String siblingEndpoint(String value, String siblingPathSegment) {
        URI uri = URI.create(value);
        String path = uri.getPath();
        int lastSlash = path.lastIndexOf('/');
        String parentPath = lastSlash >= 0 ? path.substring(0, lastSlash + 1) : "/";
        try {
            return new URI(
                    uri.getScheme(),
                    uri.getAuthority(),
                    parentPath + siblingPathSegment,
                    null,
                    null
            ).toString();
        } catch (java.net.URISyntaxException exception) {
            throw new IllegalArgumentException(
                    "Could not derive foot-type-text endpoint from ai.shoe-recommendation.url",
                    exception);
        }
    }

    static String childEndpoint(String value, String childPathSegment) {
        URI uri = URI.create(value);
        String path = uri.getPath();
        while (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        if (!path.endsWith("/" + childPathSegment)) {
            path = path + "/" + childPathSegment;
        }
        try {
            return new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    uri.getHost(),
                    uri.getPort(),
                    path,
                    uri.getQuery(),
                    uri.getFragment()
            ).toString();
        } catch (java.net.URISyntaxException exception) {
            throw new IllegalArgumentException(
                    "Could not derive child endpoint from configured URL", exception);
        }
    }

    private static String resolveFootTypeTextEndpoint(
            String batchEndpoint, String configuredFootTypeTextEndpoint) {
        if (StringUtils.hasText(configuredFootTypeTextEndpoint)) {
            return configuredFootTypeTextEndpoint;
        }
        return siblingEndpoint(batchEndpoint, "foot-type-text");
    }
}
