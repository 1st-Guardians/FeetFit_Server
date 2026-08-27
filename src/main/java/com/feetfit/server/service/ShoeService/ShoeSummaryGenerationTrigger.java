package com.feetfit.server.service.ShoeService;

import com.feetfit.server.jwt.TokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Slf4j
@Component
public class ShoeSummaryGenerationTrigger {

    private final ShoeRecommendationAiClient aiClient;
    private final TokenProvider tokenProvider;
    private final Executor summaryExecutor;
    private final Set<SummaryKey> inFlight = ConcurrentHashMap.newKeySet();

    public ShoeSummaryGenerationTrigger(
            ShoeRecommendationAiClient aiClient,
            TokenProvider tokenProvider,
            @Qualifier("shoeSummaryTaskExecutor") Executor summaryExecutor) {
        this.aiClient = aiClient;
        this.tokenProvider = tokenProvider;
        this.summaryExecutor = summaryExecutor;
    }

    public void trigger(Long userId, Long measurementSessionId, Long shoeId) {
        if (!aiClient.isEnabled()) {
            return;
        }
        SummaryKey key = new SummaryKey(measurementSessionId, shoeId);
        if (!inFlight.add(key)) {
            return;
        }
        try {
            // Claim synchronously before enqueueing so burst detail reads cannot queue the
            // same session+shoe more than once while an earlier task is waiting to run.
            summaryExecutor.execute(() -> generate(userId, measurementSessionId, shoeId, key));
        } catch (RuntimeException exception) {
            inFlight.remove(key);
            throw exception;
        }
    }

    private void generate(
            Long userId, Long measurementSessionId, Long shoeId, SummaryKey key) {
        try {
            String accessToken = tokenProvider.createAccessToken(userId);
            aiClient.requestShoeSummary(shoeId, measurementSessionId, accessToken);
        } catch (Exception exception) {
            log.warn(
                    "Shoe summary generation trigger failed. measurementSessionId={}, shoeId={}",
                    measurementSessionId, shoeId, exception);
        } finally {
            inFlight.remove(key);
        }
    }

    private record SummaryKey(Long measurementSessionId, Long shoeId) {
    }
}
