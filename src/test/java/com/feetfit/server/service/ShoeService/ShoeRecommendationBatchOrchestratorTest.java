package com.feetfit.server.service.ShoeService;

import com.feetfit.server.jwt.TokenProvider;
import com.feetfit.server.repository.ShoeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShoeRecommendationBatchOrchestratorTest {

    @Mock ShoeRepository shoeRepository;
    @Mock ShoeRecommendationRunService runService;
    @Mock ShoeRecommendationAiClient aiClient;
    @Mock TokenProvider tokenProvider;

    @Test
    void generatesAllShoesAndCompletesOnlyAfterAiCallbackSucceeds() {
        ShoeRecommendationBatchOrchestrator orchestrator = orchestrator(true);
        when(shoeRepository.count()).thenReturn(338L);
        when(runService.claimAutomaticRun(7L, 21L, 338)).thenReturn(true);
        when(tokenProvider.createAccessToken(7L)).thenReturn("service-jwt");

        orchestrator.generateOrUpdateAll(7L, 21L);

        InOrder order = inOrder(runService, aiClient);
        order.verify(runService).claimAutomaticRun(7L, 21L, 338);
        order.verify(aiClient).requestAllShoeRecommendations(21L, "service-jwt");
        order.verify(runService).completeRun(7L, 21L);
        verify(runService, never()).failRun(anyLong(), anyLong(), anyString());
    }

    @Test
    void failureIsIsolatedAndRecordedWithoutCompletingRun() {
        ShoeRecommendationBatchOrchestrator orchestrator = orchestrator(true);
        when(shoeRepository.count()).thenReturn(338L);
        when(runService.claimAutomaticRun(7L, 21L, 338)).thenReturn(true);
        when(tokenProvider.createAccessToken(7L)).thenReturn("service-jwt");
        doThrow(new IllegalStateException("AI unavailable"))
                .when(aiClient).requestAllShoeRecommendations(21L, "service-jwt");

        orchestrator.generateOrUpdateAll(7L, 21L);

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(runService).failRun(eq(7L), eq(21L), detail.capture());
        assertThat(detail.getValue()).contains("IllegalStateException").contains("AI unavailable");
        verify(runService, never()).completeRun(anyLong(), anyLong());
    }

    @Test
    void duplicateAutomaticEventDoesNotCallAiAgain() {
        ShoeRecommendationBatchOrchestrator orchestrator = orchestrator(true);
        when(shoeRepository.count()).thenReturn(338L);
        when(runService.claimAutomaticRun(7L, 21L, 338)).thenReturn(false);

        orchestrator.generateOrUpdateAll(7L, 21L);

        verifyNoInteractions(aiClient, tokenProvider);
        verify(runService, never()).completeRun(anyLong(), anyLong());
        verify(runService, never()).failRun(anyLong(), anyLong(), anyString());
    }

    @Test
    void countFailureIsLoggedWithoutAttemptingToFailAnUnclaimedRun() {
        ShoeRecommendationBatchOrchestrator orchestrator = orchestrator(true);
        when(shoeRepository.count()).thenThrow(new IllegalStateException("database unavailable"));

        orchestrator.generateOrUpdateAll(7L, 21L);

        verifyNoInteractions(runService, aiClient, tokenProvider);
    }

    @Test
    void emptyShoeDatabaseDoesNotCreateAZeroCountRun() {
        ShoeRecommendationBatchOrchestrator orchestrator = orchestrator(true);
        when(shoeRepository.count()).thenReturn(0L);

        orchestrator.generateOrUpdateAll(7L, 21L);

        verifyNoInteractions(runService, aiClient, tokenProvider);
    }

    @Test
    void disabledAutomationDoesNotReadDatabaseOrCreateRun() {
        ShoeRecommendationBatchOrchestrator orchestrator = orchestrator(false);

        orchestrator.generateOrUpdateAll(7L, 21L);

        verifyNoInteractions(shoeRepository, runService, aiClient, tokenProvider);
    }

    @Test
    void claimFailureDoesNotAttemptToFailAProbablyRolledBackRun() {
        ShoeRecommendationBatchOrchestrator orchestrator = orchestrator(true);
        when(shoeRepository.count()).thenReturn(338L);
        when(runService.claimAutomaticRun(7L, 21L, 338))
                .thenThrow(new IllegalStateException("claim failed"));

        orchestrator.generateOrUpdateAll(7L, 21L);

        verify(runService, never()).failRun(anyLong(), anyLong(), anyString());
        verifyNoInteractions(aiClient, tokenProvider);
    }

    @Test
    void explicitRetryUsesRetryClaim() {
        ShoeRecommendationBatchOrchestrator orchestrator = orchestrator(true);
        when(shoeRepository.count()).thenReturn(338L);
        when(runService.claimAutomaticRetry(
                7L, 21L, 338))
                .thenReturn(true);
        when(tokenProvider.createAccessToken(7L)).thenReturn("service-jwt");

        orchestrator.retryAll(7L, 21L);

        verify(aiClient).requestAllShoeRecommendations(21L, "service-jwt");
        verify(runService).completeRun(7L, 21L);
    }

    @Test
    void failurePersistenceFailureIsAlsoIsolatedFromMeasurementCompletion() {
        ShoeRecommendationBatchOrchestrator orchestrator = orchestrator(true);
        when(shoeRepository.count()).thenReturn(338L);
        when(runService.claimAutomaticRun(7L, 21L, 338)).thenReturn(true);
        when(tokenProvider.createAccessToken(7L)).thenReturn("service-jwt");
        doThrow(new IllegalStateException("AI unavailable"))
                .when(aiClient).requestAllShoeRecommendations(21L, "service-jwt");
        doThrow(new IllegalStateException("failure write unavailable"))
                .when(runService).failRun(eq(7L), eq(21L), anyString());

        orchestrator.generateOrUpdateAll(7L, 21L);

        verify(runService).failRun(eq(7L), eq(21L), anyString());
        verify(runService, never()).completeRun(anyLong(), anyLong());
    }

    private ShoeRecommendationBatchOrchestrator orchestrator(boolean enabled) {
        return new ShoeRecommendationBatchOrchestrator(
                shoeRepository, runService, aiClient, tokenProvider, enabled);
    }
}
