package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.domain.Device;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.domain.enums.UserStatus;
import com.feetfit.server.repository.HalluxValgusAnalysisRepository;
import com.feetfit.server.repository.MeasurementSessionRepository;
import com.feetfit.server.repository.TinaPedisAnalysisRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MeasurementCompletionServiceTest {

    @Mock
    private MeasurementSessionRepository measurementSessionRepository;

    @Mock
    private HalluxValgusAnalysisRepository halluxValgusAnalysisRepository;

    @Mock
    private TinaPedisAnalysisRepository tinaPedisAnalysisRepository;

    @Mock
    private MeasurementSocketService measurementSocketService;

    @InjectMocks
    private MeasurementCompletionService measurementCompletionService;

    @Test
    void completeIfRequiredAnalysesSaved_whenBothAnalysesExist_completesMeasurementAndSendsSocket() {
        MeasurementSession measurementSession = measurementSession(MeasurementStatus.TRANSFERRING);
        given(halluxValgusAnalysisRepository.existsByMeasurementSessionId(59L)).willReturn(true);
        given(tinaPedisAnalysisRepository.existsByMeasurementSessionId(59L)).willReturn(true);
        given(measurementSessionRepository.findByIdForCompletion(59L)).willReturn(Optional.of(measurementSession));

        measurementCompletionService.completeIfRequiredAnalysesSaved(59L);

        assertThat(measurementSession.getStatus()).isEqualTo(MeasurementStatus.COMPLETED);
        verify(measurementSocketService).sendMeasurementCompleted(measurementSession);
    }

    @Test
    void completeIfRequiredAnalysesSaved_whenOnlyOneAnalysisExists_doesNotCompleteMeasurement() {
        MeasurementSession measurementSession = measurementSession(MeasurementStatus.TRANSFERRING);
        given(halluxValgusAnalysisRepository.existsByMeasurementSessionId(59L)).willReturn(true);
        given(tinaPedisAnalysisRepository.existsByMeasurementSessionId(59L)).willReturn(false);
        given(measurementSessionRepository.findByIdForCompletion(59L)).willReturn(Optional.of(measurementSession));

        measurementCompletionService.completeIfRequiredAnalysesSaved(59L);

        assertThat(measurementSession.getStatus()).isEqualTo(MeasurementStatus.TRANSFERRING);
        verify(measurementSocketService, never()).sendMeasurementCompleted(measurementSession);
    }

    @Test
    void completeIfRequiredAnalysesSaved_whenAlreadyCompleted_doesNotSendDuplicateSocket() {
        MeasurementSession measurementSession = measurementSession(MeasurementStatus.COMPLETED);
        given(halluxValgusAnalysisRepository.existsByMeasurementSessionId(59L)).willReturn(true);
        given(tinaPedisAnalysisRepository.existsByMeasurementSessionId(59L)).willReturn(true);
        given(measurementSessionRepository.findByIdForCompletion(59L)).willReturn(Optional.of(measurementSession));

        measurementCompletionService.completeIfRequiredAnalysesSaved(59L);

        assertThat(measurementSession.getStatus()).isEqualTo(MeasurementStatus.COMPLETED);
        verify(measurementSocketService, never()).sendMeasurementCompleted(measurementSession);
    }

    private static MeasurementSession measurementSession(MeasurementStatus status) {
        return MeasurementSession.builder()
                .id(59L)
                .user(user())
                .device(device())
                .status(status)
                .measuredAt(LocalDateTime.of(2026, 6, 1, 4, 0))
                .build();
    }

    private static User user() {
        return User.builder()
                .id(1L)
                .nickname("테스트유저")
                .socialId("12345")
                .socialType(SocialType.KAKAO)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private static Device device() {
        return Device.builder()
                .id(2L)
                .deviceName("FeetFit-002")
                .build();
    }
}
