package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.service.ReportService.FootTypeTextAutomationOrchestrator;
import com.feetfit.server.service.ShoeService.ShoeRecommendationBatchOrchestrator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MeasurementCompletedAutomationOrchestratorTest {

    @Mock FootTypeTextAutomationOrchestrator footTypeTextAutomationOrchestrator;
    @Mock ShoeRecommendationBatchOrchestrator shoeRecommendationBatchOrchestrator;

    @Test
    void waitsForFootTypeTextStepBeforeStartingShoeRecommendation() {
        MeasurementCompletedAutomationOrchestrator orchestrator = orchestrator();

        orchestrator.run(7L, 21L);

        InOrder order = inOrder(
                footTypeTextAutomationOrchestrator,
                shoeRecommendationBatchOrchestrator);
        order.verify(footTypeTextAutomationOrchestrator)
                .generateIfMissing(7L, 21L);
        order.verify(shoeRecommendationBatchOrchestrator)
                .generateOrUpdateAll(7L, 21L);
    }

    @Test
    void footTypeFailureNeverBlocksShoeRecommendation() {
        MeasurementCompletedAutomationOrchestrator orchestrator = orchestrator();
        doThrow(new IllegalStateException("foot type AI unavailable"))
                .when(footTypeTextAutomationOrchestrator)
                .generateIfMissing(7L, 21L);

        assertThatCode(() -> orchestrator.run(7L, 21L))
                .doesNotThrowAnyException();

        verify(shoeRecommendationBatchOrchestrator)
                .generateOrUpdateAll(7L, 21L);
    }

    private MeasurementCompletedAutomationOrchestrator orchestrator() {
        return new MeasurementCompletedAutomationOrchestrator(
                footTypeTextAutomationOrchestrator,
                shoeRecommendationBatchOrchestrator);
    }
}
