package com.feetfit.server.service.ReportService;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FootTypeTextAutomationDispatcher {

    private final FootTypeTextAutomationOrchestrator orchestrator;

    @Async("footTypeTextTaskExecutor")
    public void dispatch(Long userId, Long measurementSessionId) {
        orchestrator.generateIfMissing(userId, measurementSessionId);
    }
}
