package com.feetfit.server.service.ShoeService;

import com.feetfit.server.apiPayload.exception.handler.ShoeHandler;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.ShoeRecommendationRun;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.ShoeRecommendationRunStatus;
import com.feetfit.server.repository.MeasurementSessionRepository;
import com.feetfit.server.repository.ShoeRecommendationRepository;
import com.feetfit.server.repository.ShoeRecommendationRunRepository;
import com.feetfit.server.repository.ShoeRecommendationReasonRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoeRecommendationRunServiceImplTest {

    @Mock MeasurementSessionRepository measurementSessionRepository;
    @Mock ShoeRecommendationRunRepository runRepository;
    @Mock ShoeRecommendationRepository recommendationRepository;
    @Mock ShoeRecommendationReasonRepository reasonRepository;
    @InjectMocks ShoeRecommendationRunServiceImpl service;

    @Test
    void startLocksMeasurementSessionBeforeRunAndIsIdempotent() {
        MeasurementSession session = completedSession();
        ShoeRecommendationRun run = pendingRun(session, 338);
        when(measurementSessionRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(session));
        when(runRepository.findByMeasurementSessionIdForUpdate(21L)).thenReturn(Optional.of(run));

        service.startRun(7L, 21L, 338, false);
        service.startRun(7L, 21L, 338, false);

        assertThat(run.getStatus()).isEqualTo(ShoeRecommendationRunStatus.RUNNING);
        InOrder order = inOrder(measurementSessionRepository, runRepository);
        order.verify(measurementSessionRepository).findByIdForUpdate(21L);
        order.verify(runRepository).findByMeasurementSessionIdForUpdate(21L);
    }

    @Test
    void automaticClaimStartsPendingRunOnlyOnce() {
        MeasurementSession session = completedSession();
        ShoeRecommendationRun run = pendingRun(session, 338);
        when(measurementSessionRepository.findByIdForUpdate(21L))
                .thenReturn(Optional.of(session));
        when(runRepository.findByMeasurementSessionIdForUpdate(21L))
                .thenReturn(Optional.of(run));

        assertThat(service.claimAutomaticRun(7L, 21L, 338)).isTrue();
        assertThat(service.claimAutomaticRun(7L, 21L, 338)).isFalse();

        assertThat(run.getStatus()).isEqualTo(ShoeRecommendationRunStatus.RUNNING);
        assertThat(run.getExpectedCount()).isEqualTo(338);
    }

    @Test
    void automaticRetryReclaimsFailedRun() {
        MeasurementSession session = completedSession();
        ShoeRecommendationRun run = pendingRun(session, 338);
        run.fail("previous attempt failed");
        when(measurementSessionRepository.findByIdForUpdate(21L))
                .thenReturn(Optional.of(session));
        when(runRepository.findByMeasurementSessionIdForUpdate(21L))
                .thenReturn(Optional.of(run));

        assertThat(service.claimAutomaticRetry(7L, 21L, 338)).isTrue();
        assertThat(run.getStatus()).isEqualTo(ShoeRecommendationRunStatus.RUNNING);
        assertThat(run.getFailureDetail()).isNull();
        assertThat(run.getStartedAt()).isAfter(LocalDateTime.now().minusMinutes(1));
    }

    @Test
    void automaticRetryDoesNotTakeOverActiveRunningRun() {
        MeasurementSession session = completedSession();
        ShoeRecommendationRun run = runningRun(session, 338);
        when(measurementSessionRepository.findByIdForUpdate(21L))
                .thenReturn(Optional.of(session));
        when(runRepository.findByMeasurementSessionIdForUpdate(21L))
                .thenReturn(Optional.of(run));

        assertThat(service.claimAutomaticRetry(7L, 21L, 338)).isFalse();
    }

    @Test
    void completedRunRequiresExplicitRestart() {
        MeasurementSession session = completedSession();
        ShoeRecommendationRun run = runningRun(session, 1);
        run.updateProcessedCount(1);
        run.complete(LocalDateTime.now());
        when(measurementSessionRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(session));
        when(runRepository.findByMeasurementSessionIdForUpdate(21L)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> service.startRun(7L, 21L, 1, false))
                .isInstanceOf(ShoeHandler.class);

        service.startRun(7L, 21L, 1, true);
        assertThat(run.getStatus()).isEqualTo(ShoeRecommendationRunStatus.RUNNING);
        assertThat(run.getProcessedCount()).isZero();
    }

    @Test
    void completeUsesAbsolutePersistedCountAndRejectsIncompleteRun() {
        MeasurementSession session = completedSession();
        ShoeRecommendationRun run = runningRun(session, 3);
        when(measurementSessionRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(session));
        when(runRepository.findByMeasurementSessionIdForUpdate(21L)).thenReturn(Optional.of(run));
        when(recommendationRepository.countByMeasurementSessionId(21L)).thenReturn(2L);

        assertThatThrownBy(() -> service.completeRun(7L, 21L))
                .isInstanceOf(ShoeHandler.class);
        assertThat(run.getProcessedCount()).isEqualTo(2);
        assertThat(run.getStatus()).isEqualTo(ShoeRecommendationRunStatus.RUNNING);
    }

    @Test
    void completeMarksRunCompletedWhenCountsMatch() {
        MeasurementSession session = completedSession();
        ShoeRecommendationRun run = runningRun(session, 3);
        when(measurementSessionRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(session));
        when(runRepository.findByMeasurementSessionIdForUpdate(21L)).thenReturn(Optional.of(run));
        when(recommendationRepository.countByMeasurementSessionId(21L)).thenReturn(3L);

        service.completeRun(7L, 21L);

        assertThat(run.getStatus()).isEqualTo(ShoeRecommendationRunStatus.COMPLETED);
        assertThat(run.getProcessedCount()).isEqualTo(3);
        assertThat(run.getCompletedAt()).isNotNull();
    }

    @Test
    void completeRejectsRecommendationWithoutThreeDistinctReasonTypes() {
        MeasurementSession session = completedSession();
        ShoeRecommendationRun run = runningRun(session, 1);
        when(measurementSessionRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(session));
        when(runRepository.findByMeasurementSessionIdForUpdate(21L)).thenReturn(Optional.of(run));
        when(recommendationRepository.countByMeasurementSessionId(21L)).thenReturn(1L);
        when(reasonRepository.countIncompleteReasonSetsByMeasurementSessionId(21L)).thenReturn(1L);

        assertThatThrownBy(() -> service.completeRun(7L, 21L))
                .isInstanceOf(ShoeHandler.class);
        assertThat(run.getStatus()).isEqualTo(ShoeRecommendationRunStatus.RUNNING);
    }

    private static MeasurementSession completedSession() {
        return MeasurementSession.builder()
                .id(21L)
                .user(User.builder().id(7L).build())
                .status(MeasurementStatus.COMPLETED)
                .measuredAt(LocalDateTime.now())
                .build();
    }

    private static ShoeRecommendationRun pendingRun(MeasurementSession session, int expected) {
        return ShoeRecommendationRun.builder()
                .id(31L)
                .measurementSession(session)
                .status(ShoeRecommendationRunStatus.PENDING)
                .expectedCount(expected)
                .processedCount(0)
                .build();
    }

    private static ShoeRecommendationRun runningRun(MeasurementSession session, int expected) {
        ShoeRecommendationRun run = pendingRun(session, expected);
        run.start(expected, false, LocalDateTime.now());
        return run;
    }
}
