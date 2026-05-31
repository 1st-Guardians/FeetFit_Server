package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.web.dto.measurement.MeasurementResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MeasurementSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendMeasurementStarted(MeasurementSession measurementSession) {
        sendMeasurementMessage(measurementSession, "MEASUREMENT_STARTED", false);
    }

    public void sendMeasurementCompleted(MeasurementSession measurementSession) {
        sendMeasurementMessage(measurementSession, "MEASUREMENT_COMPLETED", true);
    }

    public void sendTestMessage(Long userId) {
        MeasurementResponseDTO.MeasurementSocketMessageDTO message =
                MeasurementResponseDTO.MeasurementSocketMessageDTO.builder()
                        .eventType("SOCKET_TEST")
                        .userId(userId)
                        .shouldDisconnect(false)
                        .sentAt(LocalDateTime.now())
                        .build();

        messagingTemplate.convertAndSend("/topic/users/" + userId + "/measurements", message);
    }

    private void sendMeasurementMessage(MeasurementSession measurementSession, String eventType, boolean shouldDisconnect) {
        MeasurementResponseDTO.MeasurementSocketMessageDTO message =
                MeasurementResponseDTO.MeasurementSocketMessageDTO.builder()
                        .eventType(eventType)
                        .measurementSessionId(measurementSession.getId())
                        .userId(measurementSession.getUser().getId())
                        .deviceId(measurementSession.getDevice().getId())
                        .deviceName(measurementSession.getDevice().getDeviceName())
                        .status(measurementSession.getStatus())
                        .shouldDisconnect(shouldDisconnect)
                        .sentAt(LocalDateTime.now())
                        .build();

        messagingTemplate.convertAndSend("/topic/measurements/" + measurementSession.getId(), message);
        messagingTemplate.convertAndSend("/topic/users/" + measurementSession.getUser().getId() + "/measurements", message);
    }
}
