package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.service.ReportService.FootTypeTextAutomationOrchestrator;
import com.feetfit.server.service.ShoeService.ShoeRecommendationBatchOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeasurementCompletedAutomationOrchestrator {

    private final FootTypeTextAutomationOrchestrator footTypeTextAutomationOrchestrator;
    private final ShoeRecommendationBatchOrchestrator shoeRecommendationBatchOrchestrator;

    public void run(Long userId, Long measurementSessionId) {
        try {
            // Wait for the typeText save transaction to finish before the paged shoe
            // context read starts. This prevents footState from changing mid-pagination.
            footTypeTextAutomationOrchestrator.generateIfMissing(
                    userId, measurementSessionId);
        } catch (Exception exception) {
            // Foot-type text is supplementary. Its failure must never block the
            // quantitative recommendation batch for the completed measurement.
            log.error(
                    "Foot type text step failed; continuing shoe recommendation. measurementSessionId={}",
                    measurementSessionId,
                    exception);
        }

        shoeRecommendationBatchOrchestrator.generateOrUpdateAll(
                userId, measurementSessionId);
    }
}
