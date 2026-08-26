package com.feetfit.server.service.ShoeService;

import com.feetfit.server.apiPayload.exception.handler.ShoeHandler;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.ShoeRecommendationRun;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.ShoeRecommendationRunStatus;
import com.feetfit.server.repository.MeasurementSessionRepository;
import com.feetfit.server.repository.ShoeRecommendationRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoeRecommendationSessionResolverTest {

    @Mock ShoeRecommendationRunRepository runRepository;
    @Mock MeasurementSessionRepository measurementSessionRepository;
    @InjectMocks ShoeRecommendationSessionResolver resolver;

    @Test
    void currentScopeUsesOnlyLatestCompletedRun() {
        ShoeRecommendationRun completed = run(11L, 101L, ShoeRecommendationRunStatus.COMPLETED);
        when(runRepository.findLatestByUserIdAndStatus(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(ShoeRecommendationRunStatus.COMPLETED),
                any(Pageable.class))).thenReturn(List.of(completed));

        ShoeRecommendationSessionResolver.ResolvedRecommendationSession scope =
                resolver.requireCurrentCompleted(7L);

        assertThat(scope.measurementSessionId()).isEqualTo(101L);
        verify(runRepository).findLatestByUserIdAndStatus(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(ShoeRecommendationRunStatus.COMPLETED),
                any(Pageable.class));
    }

    @Test
    void explicitRunningRunIsNeverReplacedByOlderCompletedRun() {
        MeasurementSession session = session(102L);
        when(measurementSessionRepository.findById(102L)).thenReturn(Optional.of(session));
        when(runRepository.findByMeasurementSessionId(102L))
                .thenReturn(Optional.of(run(12L, 102L, ShoeRecommendationRunStatus.RUNNING)));

        assertThatThrownBy(() -> resolver.requireCompleted(7L, 102L))
                .isInstanceOf(ShoeHandler.class);
    }

    @Test
    void explicitFailedRunIsNeverReplacedByOlderCompletedRun() {
        MeasurementSession session = session(103L);
        when(measurementSessionRepository.findById(103L)).thenReturn(Optional.of(session));
        when(runRepository.findByMeasurementSessionId(103L))
                .thenReturn(Optional.of(run(13L, 103L, ShoeRecommendationRunStatus.FAILED)));

        assertThatThrownBy(() -> resolver.requireCompleted(7L, 103L))
                .isInstanceOf(ShoeHandler.class);
    }

    private static ShoeRecommendationRun run(
            Long runId, Long sessionId, ShoeRecommendationRunStatus status) {
        return ShoeRecommendationRun.builder()
                .id(runId)
                .measurementSession(session(sessionId))
                .status(status)
                .expectedCount(338)
                .processedCount(status == ShoeRecommendationRunStatus.COMPLETED ? 338 : 10)
                .startedAt(LocalDateTime.now().minusMinutes(1))
                .completedAt(status == ShoeRecommendationRunStatus.COMPLETED
                        ? LocalDateTime.now() : null)
                .build();
    }

    private static MeasurementSession session(Long sessionId) {
        return MeasurementSession.builder()
                .id(sessionId)
                .user(User.builder().id(7L).build())
                .status(MeasurementStatus.COMPLETED)
                .measuredAt(LocalDateTime.now())
                .build();
    }
}
