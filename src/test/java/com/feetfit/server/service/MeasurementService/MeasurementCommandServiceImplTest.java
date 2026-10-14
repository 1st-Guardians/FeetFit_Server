package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.apiPayload.exception.handler.MeasurementHandler;
import com.feetfit.server.domain.Device;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.enums.ConnectionStatus;
import com.feetfit.server.domain.enums.DeviceStatus;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.domain.enums.UserStatus;
import com.feetfit.server.repository.MeasurementSessionRepository;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.web.dto.measurement.MeasurementRequestDTO;
import com.feetfit.server.web.dto.measurement.MeasurementResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.any;
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
    private MeasurementCompletionService measurementCompletionService;

    @InjectMocks
    private MeasurementCommandServiceImpl measurementCommandService;

    @Test
    void createMeasurementSession_requestsMeasurementStartAfterCommit() {
        Device device = Device.builder()
                .id(1L)
                .deviceName("FeetFit-001")
                .connectionStatus(ConnectionStatus.CONNECTED)
                .status(DeviceStatus.REGISTERED)
                .build();
        User user = User.builder()
                .id(1L)
                .nickname("은서")
                .socialId("12345")
                .socialType(SocialType.KAKAO)
                .status(UserStatus.ACTIVE)
                .device(device)
                .build();
        MeasurementSession saved = MeasurementSession.builder()
                .id(1L)
                .user(user)
                .device(device)
                .measuredAt(LocalDateTime.now())
                .build();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(measurementSessionRepository.save(any(MeasurementSession.class))).willReturn(saved);

        TransactionSynchronizationManager.initSynchronization();
        try {
            MeasurementResponseDTO.CreateMeasurementSessionResultDTO response =
                    measurementCommandService.createMeasurementSession(1L, "Bearer test-token");

            assertThat(response.getStatus()).isEqualTo(MeasurementStatus.WAITING_FOR_PHOTO_ENVIRONMENT);
            verify(measurementCompletionService).initialize(saved);
            verify(measurementSocketService).sendMeasurementStatusChanged(saved);
            verify(measurementHardwareClient, never())
                    .requestMeasurementStart(1L, "Bearer test-token");

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(measurementHardwareClient)
                    .requestMeasurementStart(1L, "Bearer test-token");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void updateMeasurementStatus_toCompleted_delegatesPolicyAndReturnsUpdatedState() {
        MeasurementSession measurementSession = measurementSession(MeasurementStatus.TRANSFERRING);
        given(measurementSessionRepository.findByIdForUpdate(1L)).willReturn(Optional.of(measurementSession));
        willAnswer(invocation -> {
            measurementSession.updateStatus(MeasurementStatus.COMPLETED, 180);
            return null;
        }).given(measurementCompletionService)
                .completeMeasurementIfReady(measurementSession, 180);

        MeasurementResponseDTO.UpdateMeasurementStatusResultDTO response =
                measurementCommandService.updateMeasurementStatus(
                        1L, 1L, completedRequest(180), "Bearer test-token");

        assertThat(measurementSession.getStatus()).isEqualTo(MeasurementStatus.COMPLETED);
        assertThat(measurementSession.getMeasurementDurationSec()).isEqualTo(180);
        assertThat(response.getStatus()).isEqualTo(MeasurementStatus.COMPLETED);
        assertThat(response.getMeasurementDurationSec()).isEqualTo(180);
        verify(measurementCompletionService)
                .completeMeasurementIfReady(measurementSession, 180);
        verify(measurementSocketService, never()).sendMeasurementStatusChanged(measurementSession);
    }

    @Test
    void updateMeasurementStatus_toCompleted_whenAnalysisMissing_throwsMeasurementHandlerAndDoesNotSendSocket() {
        MeasurementSession measurementSession = measurementSession(MeasurementStatus.TRANSFERRING);
        given(measurementSessionRepository.findByIdForUpdate(1L)).willReturn(Optional.of(measurementSession));
        willThrow(new MeasurementHandler(
                com.feetfit.server.apiPayload.code.status.ErrorStatus._BAD_REQUEST))
                .given(measurementCompletionService)
                .completeMeasurementIfReady(measurementSession, 180);

        assertThatThrownBy(() -> measurementCommandService.updateMeasurementStatus(
                1L, 1L, completedRequest(180), "Bearer test-token"))
                .isInstanceOf(MeasurementHandler.class);

        assertThat(measurementSession.getStatus()).isEqualTo(MeasurementStatus.TRANSFERRING);
        verify(measurementSocketService, never()).sendMeasurementStatusChanged(measurementSession);
    }

    @Test
    void updateMeasurementStatus_toCompleted_whenAlreadyCompleted_doesNotSendDuplicateSocket() {
        MeasurementSession measurementSession = measurementSession(MeasurementStatus.COMPLETED);
        given(measurementSessionRepository.findByIdForUpdate(1L)).willReturn(Optional.of(measurementSession));

        measurementCommandService.updateMeasurementStatus(
                1L, 1L, completedRequest(180), "Bearer test-token");

        assertThat(measurementSession.getStatus()).isEqualTo(MeasurementStatus.COMPLETED);
        verify(measurementCompletionService)
                .completeMeasurementIfReady(measurementSession, 180);
        verify(measurementSocketService, never()).sendMeasurementStatusChanged(measurementSession);
    }

    @Test
    void updateMeasurementStatus_toReadyForPhotoEnvironment_requestsPhotoCaptureAfterCommit() {
        MeasurementSession measurementSession = measurementSession(MeasurementStatus.WAITING_FOR_PHOTO_ENVIRONMENT);
        given(measurementSessionRepository.findByIdForUpdate(1L)).willReturn(Optional.of(measurementSession));

        TransactionSynchronizationManager.initSynchronization();
        try {
            MeasurementResponseDTO.UpdateMeasurementStatusResultDTO response =
                    measurementCommandService.updateMeasurementStatus(
                            1L,
                            1L,
                            new MeasurementRequestDTO.UpdateMeasurementStatusDTO(
                                    MeasurementStatus.READY_FOR_PHOTO_ENVIRONMENT,
                                    null
                            ),
                            "Bearer test-token"
                    );

            assertThat(response.getStatus()).isEqualTo(MeasurementStatus.READY_FOR_PHOTO_ENVIRONMENT);
            verify(measurementSocketService).sendMeasurementStatusChanged(measurementSession);
            verify(measurementHardwareClient, never())
                    .requestPhotoCapture(1L, "Bearer test-token");

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(measurementHardwareClient)
                    .requestPhotoCapture(1L, "Bearer test-token");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void updateMeasurementStatus_toWaitingForEnvironment_requestsEnvironmentAfterCommitWithoutSocket() {
        MeasurementSession measurementSession = measurementSession(MeasurementStatus.CAPTURING_PHOTO_ENVIRONMENT);
        given(measurementSessionRepository.findByIdForUpdate(1L)).willReturn(Optional.of(measurementSession));

        TransactionSynchronizationManager.initSynchronization();
        try {
            MeasurementResponseDTO.UpdateMeasurementStatusResultDTO response =
                    measurementCommandService.updateMeasurementStatus(
                            1L,
                            1L,
                            new MeasurementRequestDTO.UpdateMeasurementStatusDTO(
                                    MeasurementStatus.WAITING_FOR_ENVIRONMENT,
                                    null
                            ),
                            "Bearer test-token"
                    );

            assertThat(response.getStatus()).isEqualTo(MeasurementStatus.WAITING_FOR_ENVIRONMENT);
            verify(measurementSocketService, never()).sendMeasurementStatusChanged(measurementSession);
            verify(measurementCompletionService)
                    .refreshCaptureCompletedByStatus(measurementSession, MeasurementStatus.WAITING_FOR_ENVIRONMENT);
            verify(measurementHardwareClient, never())
                    .requestEnvironmentMeasurement(1L, "Bearer test-token");

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(measurementHardwareClient)
                    .requestEnvironmentMeasurement(1L, "Bearer test-token");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void updateMeasurementStatus_normalizesLegacyCapturingPhotoToIntegratedStatus() {
        MeasurementSession measurementSession = measurementSession(MeasurementStatus.READY_FOR_PHOTO_ENVIRONMENT);
        given(measurementSessionRepository.findByIdForUpdate(1L)).willReturn(Optional.of(measurementSession));

        MeasurementResponseDTO.UpdateMeasurementStatusResultDTO response =
                measurementCommandService.updateMeasurementStatus(
                        1L,
                        1L,
                        new MeasurementRequestDTO.UpdateMeasurementStatusDTO(
                                MeasurementStatus.CAPTURING_PHOTO,
                                null
                        ),
                        "Bearer test-token"
                );

        assertThat(response.getStatus()).isEqualTo(MeasurementStatus.CAPTURING_PHOTO_ENVIRONMENT);
        verify(measurementSocketService).sendMeasurementStatusChanged(measurementSession);
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
