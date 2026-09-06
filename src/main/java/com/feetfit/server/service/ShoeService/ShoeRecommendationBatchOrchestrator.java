package com.feetfit.server.service.ShoeService;

import com.feetfit.server.jwt.TokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;


@Slf4j
@Service
public class ShoeRecommendationBatchOrchestrator {

    private static final int FAILURE_DETAIL_LIMIT = 4000;

    private final ShoeRecommendationEligibilityService eligibilityService;
    private final ShoeRecommendationRunService runService;
    private final ShoeRecommendationAiClient aiClient;
    private final TokenProvider tokenProvider;
    private final boolean enabled;

    public ShoeRecommendationBatchOrchestrator(
            ShoeRecommendationEligibilityService eligibilityService,
            ShoeRecommendationRunService runService,
            ShoeRecommendationAiClient aiClient,
            TokenProvider tokenProvider,
            @Value("${ai.shoe-recommendation.enabled:true}") boolean enabled) {
        this.eligibilityService = eligibilityService;
        this.runService = runService;
        this.aiClient = aiClient;
        this.tokenProvider = tokenProvider;
        this.enabled = enabled;
    }

    public void generateOrUpdateAll(Long userId, Long measurementSessionId) {
        execute(userId, measurementSessionId, false);
    }

    public void retryAll(Long userId, Long measurementSessionId) {
        execute(userId, measurementSessionId, true);
    }

    private void execute(Long userId, Long measurementSessionId, boolean recovery) {
        if (!enabled) {
            log.info("Automatic shoe recommendation is disabled. measurementSessionId={}",
                    measurementSessionId);
            return;
        }

        boolean claimed = false;
        try {
            long shoeCount = eligibilityService.countEligibleShoes();
            if (shoeCount <= 0) {
                log.warn(
                        "Automatic shoe recommendation skipped because no eligible shoes exist. measurementSessionId={}",
                        measurementSessionId);
                return;
            }
            int expectedCount = Math.toIntExact(shoeCount);
            claimed = recovery
                    ? runService.claimAutomaticRetry(
                            userId, measurementSessionId, expectedCount)
                    : runService.claimAutomaticRun(
                            userId, measurementSessionId, expectedCount);
            if (!claimed) {
                log.info(
                        "Automatic shoe recommendation already running or completed. measurementSessionId={}",
                        measurementSessionId);
                return;
            }
            String accessToken = tokenProvider.createAccessToken(userId);
            aiClient.requestAllShoeRecommendations(measurementSessionId, accessToken);
            runService.completeRun(userId, measurementSessionId);
            log.info(
                    "Automatic shoe recommendation completed. measurementSessionId={}, expectedCount={}",
                    measurementSessionId, expectedCount);
        } catch (Exception exception) {
            String failureDetail = failureDetail(exception);
            log.error(
                    "Automatic shoe recommendation failed. measurementSessionId={}, detail={}",
                    measurementSessionId, failureDetail, exception);
            if (claimed) {
                try {
                    runService.failRun(userId, measurementSessionId, failureDetail);
                } catch (Exception failException) {
                    log.error(
                            "Failed to persist recommendation run failure. measurementSessionId={}",
                            measurementSessionId, failException);
                }
            }
        }
    }

    private static String failureDetail(Exception exception) {
        String message = exception.getMessage();
        String detail = exception.getClass().getSimpleName()
                + (StringUtils.hasText(message) ? ": " + message : "");
        return detail.length() <= FAILURE_DETAIL_LIMIT
                ? detail
                : detail.substring(0, FAILURE_DETAIL_LIMIT);
    }
}
