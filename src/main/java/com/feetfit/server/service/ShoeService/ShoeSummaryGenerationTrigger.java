package com.feetfit.server.service.ShoeService;

import com.feetfit.server.jwt.TokenProvider;
import com.feetfit.server.web.dto.shoe.ShoeRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class ShoeSummaryGenerationTrigger {

    private static final long CONCURRENT_RESULT_WAIT_SECONDS = 12L;

    private final ShoeRecommendationAiClient aiClient;
    private final TokenProvider tokenProvider;
    private final ShoeCommandService shoeCommandService;
    private final ConcurrentHashMap<SummaryKey, CompletableFuture<Boolean>> inFlight =
            new ConcurrentHashMap<>();

    public ShoeSummaryGenerationTrigger(
            ShoeRecommendationAiClient aiClient,
            TokenProvider tokenProvider,
            ShoeCommandService shoeCommandService) {
        this.aiClient = aiClient;
        this.tokenProvider = tokenProvider;
        this.shoeCommandService = shoeCommandService;
    }

    /**
     * Generates and persists a complete summary before returning. Concurrent reads for the
     * same session and shoe share the owner's result instead of racing the delete/reinsert
     * validation path in {@link ShoeCommandService#saveShoeSummaries}.
     */
    public boolean generateNow(Long userId, Long measurementSessionId, Long shoeId) {
        if (!aiClient.isEnabled()) {
            return false;
        }
        SummaryKey key = new SummaryKey(measurementSessionId, shoeId);
        CompletableFuture<Boolean> owned = new CompletableFuture<>();
        CompletableFuture<Boolean> active = inFlight.putIfAbsent(key, owned);
        if (active != null) {
            return await(active, key);
        }

        try {
            String accessToken = tokenProvider.createAccessToken(userId);
            ShoeRequestDTO.SaveShoeSummariesDTO summary =
                    aiClient.generateShoeSummary(shoeId, measurementSessionId, accessToken);
            if (!measurementSessionId.equals(summary.getMeasurementSessionId())) {
                throw new IllegalStateException(
                        "Feetfit_AI returned a summary for a different measurement session");
            }
            shoeCommandService.saveShoeSummaries(userId, shoeId, summary);
            owned.complete(true);
            return true;
        } catch (Exception exception) {
            owned.complete(false);
            log.warn(
                    "Synchronous shoe summary generation failed. measurementSessionId={}, shoeId={}",
                    measurementSessionId, shoeId, exception);
            return false;
        } finally {
            inFlight.remove(key, owned);
        }
    }

    private boolean await(CompletableFuture<Boolean> active, SummaryKey key) {
        try {
            // The owner can use the full 10-second AI timeout; leave a small allowance for
            // response decoding and the local transaction that persists the validated DTO.
            return active.get(CONCURRENT_RESULT_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn(
                    "Interrupted while awaiting shoe summary. measurementSessionId={}, shoeId={}",
                    key.measurementSessionId(), key.shoeId());
            return false;
        } catch (TimeoutException exception) {
            log.warn(
                    "Timed out awaiting concurrent shoe summary. measurementSessionId={}, shoeId={}",
                    key.measurementSessionId(), key.shoeId());
            return false;
        } catch (Exception exception) {
            log.warn(
                    "Failed while awaiting concurrent shoe summary. measurementSessionId={}, shoeId={}",
                    key.measurementSessionId(), key.shoeId(), exception);
            return false;
        }
    }

    private record SummaryKey(Long measurementSessionId, Long shoeId) {
    }
}
