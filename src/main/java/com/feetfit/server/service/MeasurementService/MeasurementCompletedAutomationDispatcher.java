package com.feetfit.server.service.MeasurementService;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MeasurementCompletedAutomationDispatcher {

    private final MeasurementCompletedAutomationOrchestrator orchestrator;

    @Async("measurementCompletionAutomationTaskExecutor")
    public void dispatch(Long userId, Long measurementSessionId) {
        orchestrator.run(userId, measurementSessionId);
    }
}
