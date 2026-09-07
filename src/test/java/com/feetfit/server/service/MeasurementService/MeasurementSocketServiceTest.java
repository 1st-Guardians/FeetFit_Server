package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.domain.Device;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.MeasurementFailureReason;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.domain.enums.UserStatus;
import com.feetfit.server.web.dto.measurement.MeasurementResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class MeasurementSocketServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private MeasurementSocketService measurementSocketService;

    @Test
    void sendMeasurementCompleted_sendsCompletedMessageToMeasurementAndUserTopics() {
        MeasurementSession measurementSession = MeasurementSession.builder()
                .id(43L)
                .user(user())
                .device(device())
                .status(MeasurementStatus.COMPLETED)
                .measuredAt(LocalDateTime.of(2026, 6, 1, 2, 30))
                .build();

        measurementSocketService.sendMeasurementStatusChanged(measurementSession);

        ArgumentCaptor<MeasurementResponseDTO.MeasurementSocketMessageDTO> measurementTopicCaptor =
                ArgumentCaptor.forClass(MeasurementResponseDTO.MeasurementSocketMessageDTO.class);
        ArgumentCaptor<MeasurementResponseDTO.MeasurementSocketMessageDTO> userTopicCaptor =
                ArgumentCaptor.forClass(MeasurementResponseDTO.MeasurementSocketMessageDTO.class);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/measurements/43"),
                measurementTopicCaptor.capture()
        );
        verify(messagingTemplate).convertAndSend(
                eq("/topic/users/1/measurements"),
                userTopicCaptor.capture()
        );

        assertCompletedMessage(measurementTopicCaptor.getValue());
        assertCompletedMessage(userTopicCaptor.getValue());
    }

    private static void assertCompletedMessage(MeasurementResponseDTO.MeasurementSocketMessageDTO message) {
        assertThat(message.getEventType()).isEqualTo("MEASUREMENT_COMPLETED");
        assertThat(message.getMeasurementSessionId()).isEqualTo(43L);
        assertThat(message.getUserId()).isEqualTo(1L);
        assertThat(message.getDeviceId()).isEqualTo(2L);
        assertThat(message.getDeviceName()).isEqualTo("FeetFit-002");
        assertThat(message.getStatus()).isEqualTo(MeasurementStatus.COMPLETED);
        assertThat(message.getShouldDisconnect()).isTrue();
        assertThat(message.getSentAt()).isNotNull();
    }

    @Test
    void recaptureMessageKeepsSocketOpenAndDoesNotExposeTerminalFailureFields() {
        MeasurementSession session = MeasurementSession.builder().id(43L).user(user()).device(device()).build();
        session.requirePhotoRecapture("ArUco marker is covered");

        measurementSocketService.sendMeasurementStatusChanged(session);

        var captor = ArgumentCaptor.forClass(MeasurementResponseDTO.MeasurementSocketMessageDTO.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/measurements/43"), captor.capture());
        var message = captor.getValue();
        assertThat(message.getEventType()).isEqualTo("MEASUREMENT_STATUS_CHANGED");
        assertThat(message.getStatus()).isEqualTo(MeasurementStatus.WAITING_FOR_RECAPTURE);
        assertThat(message.getMessage()).isEqualTo("사진을 다시 촬영해야 합니다.");
        assertThat(message.getStatusMessage()).isEqualTo(message.getMessage());
        assertThat(message.getDetail()).isEqualTo("ArUco marker is covered");
        assertThat(message.getShouldDisconnect()).isFalse();
        assertThat(message.getFailureReason()).isNull();
        assertThat(message.getFailureDetail()).isNull();
        assertThat(message.getPhotoCaptureAttempt()).isZero();
        assertThat(message.getRemainingPhotoRecaptures()).isEqualTo(3);
        verify(messagingTemplate).convertAndSend("/topic/users/1/measurements", message);
    }

    @Test
    void exhaustedRecaptureEmitsFinalFailureAndDisconnect() {
        MeasurementSession session = MeasurementSession.builder().id(43L).user(user()).device(device())
                .status(MeasurementStatus.FAILED).photoRecaptureCount(3)
                .failureReason(MeasurementFailureReason.INVALID_CAPTURE_DATA).failureDetail("Repeated marker failure").build();

        measurementSocketService.sendMeasurementStatusChanged(session);

        var captor = ArgumentCaptor.forClass(MeasurementResponseDTO.MeasurementSocketMessageDTO.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/measurements/43"), captor.capture());
        var message = captor.getValue();
        assertThat(message.getEventType()).isEqualTo("MEASUREMENT_FAILED");
        assertThat(message.getFailureReason()).isEqualTo(MeasurementFailureReason.INVALID_CAPTURE_DATA);
        assertThat(message.getFailureMessage()).isEqualTo("사진 촬영을 완료하지 못했습니다.");
        assertThat(message.getFailureDetail()).isEqualTo("Repeated marker failure");
        assertThat(message.getShouldDisconnect()).isTrue();
        assertThat(message.getRemainingPhotoRecaptures()).isZero();
    }

    @Test
    void transactionalMessagesAreSnapshotsPublishedOnlyAfterCommit() {
        MeasurementSession session = MeasurementSession.builder().id(43L).user(user()).device(device()).build();
        session.requirePhotoRecapture("marker covered");
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            measurementSocketService.sendMeasurementStatusChanged(session);
            session.startPhotoRecapture();
            session.updateStatus(MeasurementStatus.READY_FOR_RECAPTURE, null);
            measurementSocketService.sendMeasurementStatusChanged(session);
            verifyNoInteractions(messagingTemplate);

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

            var captor = ArgumentCaptor.forClass(MeasurementResponseDTO.MeasurementSocketMessageDTO.class);
            verify(messagingTemplate, times(2)).convertAndSend(eq("/topic/measurements/43"), captor.capture());
            assertThat(captor.getAllValues()).extracting(MeasurementResponseDTO.MeasurementSocketMessageDTO::getStatus)
                    .containsExactly(MeasurementStatus.WAITING_FOR_RECAPTURE, MeasurementStatus.READY_FOR_RECAPTURE);
            assertThat(captor.getAllValues()).extracting(MeasurementResponseDTO.MeasurementSocketMessageDTO::getPhotoCaptureAttempt)
                    .containsExactly(0, 1);
        } finally {
            TransactionSynchronizationManager.clear();
        }
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
