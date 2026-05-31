package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.apiPayload.exception.handler.MeasurementHandler;
import com.feetfit.server.domain.Device;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.domain.enums.UserStatus;
import com.feetfit.server.repository.HalluxValgusAnalysisRepository;
import com.feetfit.server.repository.MeasurementSessionRepository;
import com.feetfit.server.repository.TinaPedisAnalysisRepository;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.web.dto.measurement.MeasurementRequestDTO;
import com.feetfit.server.web.dto.measurement.MeasurementResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MeasurementCommandServiceImplTest {

    @Mock
    private MeasurementSessionRepository measurementSessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MeasurementSocketService measurementSocketService;

    @Mock
    private MeasurementHardwareClient measurementHardwareClient;

    @Mock
    private TinaPedisAnalysisRepository tinaPedisAnalysisRepository;

    @Mock
    private HalluxValgusAnalysisRepository halluxValgusAnalysisRepository;

    @InjectMocks
    private MeasurementCommandServiceImpl measurementCommandService;

    @Test
    void updateMeasurementStatus_toCompleted_whenAnalysesReady_marksCompletedAndSendsSocket() {
        MeasurementSession measurementSession = measurementSession(MeasurementStatus.TRANSFERRING);
        given(measurementSessionRepository.findById(1L)).willReturn(Optional.of(measurementSession));
        given(halluxValgusAnalysisRepository.existsByMeasurementSessionId(1L)).willReturn(true);
        given(tinaPedisAnalysisRepository.existsByMeasurementSessionId(1L)).willReturn(true);

        MeasurementResponseDTO.UpdateMeasurementStatusResultDTO response =
                measurementCommandService.updateMeasurementStatus(1L, 1L, completedRequest(180));

        assertThat(measurementSession.getStatus()).isEqualTo(MeasurementStatus.COMPLETED);
        assertThat(measurementSession.getMeasurementDurationSec()).isEqualTo(180);
        assertThat(response.getStatus()).isEqualTo(MeasurementStatus.COMPLETED);
        assertThat(response.getMeasurementDurationSec()).isEqualTo(180);
        verify(measurementSocketService).sendMeasurementCompleted(measurementSession);
    }

    @Test
    void updateMeasurementStatus_toCompleted_whenAnalysisMissing_throwsMeasurementHandlerAndDoesNotSendSocket() {
        MeasurementSession measurementSession = measurementSession(MeasurementStatus.TRANSFERRING);
        given(measurementSessionRepository.findById(1L)).willReturn(Optional.of(measurementSession));
        given(halluxValgusAnalysisRepository.existsByMeasurementSessionId(1L)).willReturn(true);
        given(tinaPedisAnalysisRepository.existsByMeasurementSessionId(1L)).willReturn(false);

        assertThatThrownBy(() -> measurementCommandService.updateMeasurementStatus(1L, 1L, completedRequest(180)))
                .isInstanceOf(MeasurementHandler.class);

        assertThat(measurementSession.getStatus()).isEqualTo(MeasurementStatus.TRANSFERRING);
        verify(measurementSocketService, never()).sendMeasurementCompleted(measurementSession);
    }

    @Test
    void updateMeasurementStatus_toCompleted_whenAlreadyCompleted_doesNotSendDuplicateSocket() {
        MeasurementSession measurementSession = measurementSession(MeasurementStatus.COMPLETED);
        given(measurementSessionRepository.findById(1L)).willReturn(Optional.of(measurementSession));
        given(halluxValgusAnalysisRepository.existsByMeasurementSessionId(1L)).willReturn(true);
        given(tinaPedisAnalysisRepository.existsByMeasurementSessionId(1L)).willReturn(true);

        measurementCommandService.updateMeasurementStatus(1L, 1L, completedRequest(180));

        assertThat(measurementSession.getStatus()).isEqualTo(MeasurementStatus.COMPLETED);
        verify(measurementSocketService, never()).sendMeasurementCompleted(measurementSession);
    }

    private static MeasurementSession measurementSession(MeasurementStatus status) {
        return MeasurementSession.builder()
                .id(1L)
                .user(user())
                .device(device())
                .status(status)
                .measuredAt(LocalDateTime.now().minusMinutes(3))
                .build();
    }

    private static MeasurementRequestDTO.UpdateMeasurementStatusDTO completedRequest(Integer measurementDurationSec) {
        return new MeasurementRequestDTO.UpdateMeasurementStatusDTO(MeasurementStatus.COMPLETED, measurementDurationSec);
    }

    private static User user() {
        return User.builder()
                .id(1L)
                .nickname("은서")
                .socialId("12345")
                .socialType(SocialType.KAKAO)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private static Device device() {
        return Device.builder()
                .id(1L)
                .deviceName("FeetFit-001")
                .build();
    }
}
