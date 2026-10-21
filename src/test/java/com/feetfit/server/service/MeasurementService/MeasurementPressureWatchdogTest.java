package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.MetricType;
import com.feetfit.server.repository.MeasurementSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MeasurementPressureWatchdogTest {
    private final MeasurementSessionRepository sessions = mock(MeasurementSessionRepository.class);
    private final MeasurementPressureRecoveryService recovery = mock(MeasurementPressureRecoveryService.class);
    private final MeasurementPressureWatchdog watchdog = new MeasurementPressureWatchdog(sessions, recovery);

    @BeforeEach
    void setup() {
        when(sessions.findPressureTimeoutCandidates(anySet(), any(), eq(MetricType.PRESSURE_BALANCE), any()))
                .thenReturn(List.of());
    }

    @Test
    void scansOnlyActivePressurePhasesWithBoundedBatchesAndSeparateDeadlines() {
        LocalDateTime before = LocalDateTime.now();
        watchdog.recoverStalledSessions();
        LocalDateTime after = LocalDateTime.now();

        assertDeadline(Set.of(MeasurementStatus.READY_FOR_PRESSURE, MeasurementStatus.READY_FOR_PRESSURE_RETRY),
                before.minusSeconds(30), after.minusSeconds(30));
        assertDeadline(Set.of(MeasurementStatus.MEASURING_PRESSURE), before.minusSeconds(30), after.minusSeconds(30));
        assertDeadline(Set.of(MeasurementStatus.ANALYZING), before.minusSeconds(600), after.minusSeconds(600));
        verifyNoMoreInteractions(sessions);
        verifyNoInteractions(recovery);
    }

    @Test
    void configurableDeadlineIsPassedToRecoveryWithTheObservedAttemptAndState() {
        ReflectionTestUtils.setField(watchdog, "measurementTimeoutSeconds", 17L);
        var candidate = candidate(12L, 2);
        when(sessions.findPressureTimeoutCandidates(eq(Set.of(MeasurementStatus.MEASURING_PRESSURE)), any(), any(), any()))
                .thenReturn(List.of(candidate));
        LocalDateTime before = LocalDateTime.now().minusSeconds(17);

        watchdog.recoverStalledSessions();

        var cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(recovery).recoverStalledPressure(eq(12L), eq(2), eq(MeasurementStatus.MEASURING_PRESSURE), cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before, LocalDateTime.now().minusSeconds(17));
    }

    @Test
    void oneFailedRecoveryDoesNotBlockOtherSessions() {
        var candidates = List.of(candidate(12L, 0), candidate(13L, 1));
        when(sessions.findPressureTimeoutCandidates(eq(Set.of(MeasurementStatus.MEASURING_PRESSURE)), any(), any(), any()))
                .thenReturn(candidates);
        doThrow(new IllegalStateException("database unavailable"))
                .when(recovery).recoverStalledPressure(eq(12L), anyInt(), any(), any());

        watchdog.recoverStalledSessions();

        verify(recovery).recoverStalledPressure(eq(13L), eq(1), eq(MeasurementStatus.MEASURING_PRESSURE), any());
    }

    @Test
    void oneFailedScanDoesNotBlockOtherPhases() {
        when(sessions.findPressureTimeoutCandidates(
                eq(Set.of(MeasurementStatus.READY_FOR_PRESSURE, MeasurementStatus.READY_FOR_PRESSURE_RETRY)), any(), any(), any()))
                .thenThrow(new IllegalStateException("database temporarily unavailable"));

        watchdog.recoverStalledSessions();

        verify(sessions).findPressureTimeoutCandidates(eq(Set.of(MeasurementStatus.MEASURING_PRESSURE)), any(), any(), any());
        verify(sessions).findPressureTimeoutCandidates(eq(Set.of(MeasurementStatus.ANALYZING)), any(), any(), any());
    }

    private void assertDeadline(Set<MeasurementStatus> statuses, LocalDateTime lower, LocalDateTime upper) {
        var cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(sessions).findPressureTimeoutCandidates(eq(statuses), cutoff.capture(),
                eq(MetricType.PRESSURE_BALANCE), eq(PageRequest.of(0, 100)));
        assertThat(cutoff.getValue()).isBetween(lower, upper);
    }

    private MeasurementSessionRepository.PressureTimeoutCandidate candidate(Long id, int attempt) {
        var candidate = mock(MeasurementSessionRepository.PressureTimeoutCandidate.class);
        when(candidate.getId()).thenReturn(id);
        when(candidate.getPressureRetryCount()).thenReturn(attempt);
        when(candidate.getStatus()).thenReturn(MeasurementStatus.MEASURING_PRESSURE);
        return candidate;
    }
}
