package com.feetfit.server.service.ShoeService;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShoeRecommendationAutomationDispatcher {

    private final ShoeRecommendationBatchOrchestrator orchestrator;

    @Async("measurementCompletionAutomationTaskExecutor")
    public void retry(Long userId, Long measurementSessionId) {
        orchestrator.retryAll(userId, measurementSessionId);
    }
}
