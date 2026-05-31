package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.domain.Device;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.enums.MeasurementStatus;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

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

        measurementSocketService.sendMeasurementCompleted(measurementSession);

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
