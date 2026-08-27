package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.domain.MeasurementAnalysisStatus;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.event.MeasurementCompletedEvent;
import com.feetfit.server.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeasurementCompletionServiceTest {

    @Mock MeasurementAnalysisStatusRepository measurementAnalysisStatusRepository;
    @Mock HalluxValgusAnalysisRepository halluxValgusAnalysisRepository;
    @Mock TinaPedisAnalysisRepository tinaPedisAnalysisRepository;
    @Mock DailyFootAnalysisRepository dailyFootAnalysisRepository;
    @Mock PressureSensorReadingRepository pressureSensorReadingRepository;
    @Mock ReportRepository reportRepository;
    @Mock MetricAnalysisResultRepository metricAnalysisResultRepository;
    @Mock MeasurementSessionRepository measurementSessionRepository;
    @Mock MeasurementSocketService measurementSocketService;
    @Mock ApplicationEventPublisher applicationEventPublisher;
    @Mock MeasurementCareTipsGenerationService measurementCareTipsGenerationService;
    @InjectMocks MeasurementCompletionService service;

    @Test
    void publishesExactlyOneCompletionEventWhenReadySessionTransitions() {
        MeasurementSession session = session(MeasurementStatus.ANALYZING);
        MeasurementAnalysisStatus ready = readyStatus(session);
        when(measurementSessionRepository.findByIdForUpdate(21L))
                .thenReturn(Optional.of(session));
        when(measurementAnalysisStatusRepository.findByMeasurementSessionIdForUpdate(21L))
                .thenReturn(Optional.of(ready));

        service.completeMeasurementIfReady(session, 180);

        assertThat(session.getStatus()).isEqualTo(MeasurementStatus.COMPLETED);
        ArgumentCaptor<MeasurementCompletedEvent> event =
                ArgumentCaptor.forClass(MeasurementCompletedEvent.class);
        verify(applicationEventPublisher).publishEvent(event.capture());
        assertThat(event.getValue()).isEqualTo(new MeasurementCompletedEvent(21L, 7L));
        verify(measurementCareTipsGenerationService).generateAndSaveIfNeeded(session);
        verify(measurementSocketService).sendMeasurementStatusChanged(session);
    }

    @Test
    void completedSessionDoesNotPublishDuplicateEvent() {
        MeasurementSession session = session(MeasurementStatus.COMPLETED);
        MeasurementAnalysisStatus ready = readyStatus(session);
        when(measurementSessionRepository.findByIdForUpdate(21L))
                .thenReturn(Optional.of(session));
        when(measurementAnalysisStatusRepository.findByMeasurementSessionIdForUpdate(21L))
                .thenReturn(Optional.of(ready));

        service.completeMeasurementIfReady(session, 180);

        verifyNoInteractions(
                applicationEventPublisher,
                measurementSocketService,
                measurementCareTipsGenerationService
        );
    }

    private static MeasurementSession session(MeasurementStatus status) {
        return MeasurementSession.builder()
                .id(21L)
                .user(User.builder().id(7L).build())
                .status(status)
                .measuredAt(LocalDateTime.now().minusMinutes(3))
                .build();
    }

    private static MeasurementAnalysisStatus readyStatus(MeasurementSession session) {
        return MeasurementAnalysisStatus.builder()
                .measurementSession(session)
                .photoCaptureCompleted(true)
                .photoAnalysisCompleted(true)
                .pressureCaptureCompleted(true)
                .pressureAnalysisCompleted(true)
                .environmentAnalysisCompleted(true)
                .metricReportCompleted(true)
                .build();
    }
}
