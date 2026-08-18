package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.web.dto.measurement.MeasurementResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeasurementSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendMeasurementStatusChanged(MeasurementSession measurementSession) {
        MeasurementStatus status = measurementSession.getStatus();
        boolean shouldDisconnect = status == MeasurementStatus.COMPLETED || status == MeasurementStatus.FAILED;
        String eventType = switch (status) {
            case COMPLETED -> "MEASUREMENT_COMPLETED";
            case FAILED -> "MEASUREMENT_FAILED";
            default -> "MEASUREMENT_STATUS_CHANGED";
        };

        sendMeasurementMessage(measurementSession, eventType, shouldDisconnect);
    }

    public void sendTestMessage(Long userId) {
        MeasurementResponseDTO.MeasurementSocketMessageDTO message =
                MeasurementResponseDTO.MeasurementSocketMessageDTO.builder()
                        .eventType("SOCKET_TEST")
                        .userId(userId)
                        .shouldDisconnect(false)
                        .sentAt(LocalDateTime.now())
                        .build();

        String destination = "/topic/users/" + userId + "/measurements";
        messagingTemplate.convertAndSend(destination, message);
        log.info("Measurement socket message sent. destination={}, eventType={}, userId={}",
                destination,
                message.getEventType(),
                userId
        );
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
                        .statusMessage(resolveStatusMessage(measurementSession.getStatus()))
                        .shouldDisconnect(shouldDisconnect)
                        .sentAt(LocalDateTime.now())
                        .build();

        String measurementDestination = "/topic/measurements/" + measurementSession.getId();
        String userDestination = "/topic/users/" + measurementSession.getUser().getId() + "/measurements";

        messagingTemplate.convertAndSend(measurementDestination, message);
        messagingTemplate.convertAndSend(userDestination, message);
        log.info("Measurement socket message sent. eventType={}, measurementDestination={}, userDestination={}, measurementSessionId={}, status={}, shouldDisconnect={}",
                eventType,
                measurementDestination,
                userDestination,
                measurementSession.getId(),
                measurementSession.getStatus(),
                shouldDisconnect
        );
    }

    private String resolveStatusMessage(MeasurementStatus status) {
        return switch (status) {
            case WAITING_FOR_PHOTO -> "FSR 센서 판을 올리고 유리판 위에 올라와 주세요.";
            case READY_FOR_PHOTO -> "사진 촬영 준비가 완료되었습니다. 촬영을 시작합니다.";
            case CAPTURING_PHOTO -> "사진을 촬영하고 있습니다. 잠시 움직이지 말아 주세요.";
            case WAITING_FOR_PRESSURE -> "촬영이 완료되었습니다. 내려온 뒤 FSR 센서 판을 내리고 다시 올라와 주세요.";
            case READY_FOR_PRESSURE -> "압력 측정 준비가 완료되었습니다. 압력 측정을 시작합니다.";
            case MEASURING_PRESSURE -> "발 압력을 측정하고 있습니다. 잠시 움직이지 말아 주세요.";
            case PROCESSING, TRANSFERRING -> "측정이 완료되었습니다. 결과를 분석하고 있습니다.";
            case COMPLETED -> "분석이 완료되었습니다. 결과를 확인해 주세요.";
            case FAILED -> "측정 중 문제가 발생했습니다. 다시 시도해 주세요.";
            case PENDING -> "측정을 준비하고 있습니다.";
            case MEASURING -> "측정 중입니다. 잠시 움직이지 말아 주세요.";
        };
    }
}
