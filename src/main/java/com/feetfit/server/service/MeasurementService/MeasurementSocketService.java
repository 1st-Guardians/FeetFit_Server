package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.web.dto.measurement.MeasurementResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    public void sendMeasurementCompleted(MeasurementSession session, Long reportId) {
        sendMeasurementMessage(session, "MEASUREMENT_COMPLETED", true, reportId, "PENDING");
    }

    private void sendMeasurementMessage(MeasurementSession measurementSession, String eventType, boolean shouldDisconnect) {
        sendMeasurementMessage(measurementSession, eventType, shouldDisconnect, null, null);
    }

    private void sendMeasurementMessage(MeasurementSession measurementSession, String eventType, boolean shouldDisconnect,
                                        Long reportId, String recommendationStatus) {
        String messageText = resolveStatusMessage(measurementSession.getStatus());
        MeasurementResponseDTO.MeasurementSocketMessageDTO message =
                MeasurementResponseDTO.MeasurementSocketMessageDTO.builder()
                        .eventType(eventType)
                        .measurementSessionId(measurementSession.getId())
                        .userId(measurementSession.getUser().getId())
                        .deviceId(measurementSession.getDevice().getId())
                        .deviceName(measurementSession.getDevice().getDeviceName())
                        .status(measurementSession.getStatus())
                        .statusMessage(messageText)
                        .message(messageText)
                        .reportId(reportId)
                        .drugRecommendationStatus(recommendationStatus)
                        .detail(resolveRetryDetail(measurementSession))
                        .photoCaptureAttempt(measurementSession.getPhotoRecaptureCount())
                        .remainingPhotoRecaptures(measurementSession.getRemainingPhotoRecaptures())
                        .pressureMeasurementAttempt(measurementSession.getPressureRetryCount())
                        .remainingPressureRetries(measurementSession.getRemainingPressureRetries())
                        .failureReason(measurementSession.getFailureReason())
                        .failureMessage(MeasurementFailureMessageResolver.resolve(measurementSession.getFailureReason()))
                        .failureDetail(measurementSession.getFailureDetail())
                        .shouldDisconnect(shouldDisconnect)
                        .sentAt(LocalDateTime.now())
                        .build();

        String measurementDestination = "/topic/measurements/" + measurementSession.getId();
        String userDestination = "/topic/users/" + measurementSession.getUser().getId() + "/measurements";

        // Publish the immutable snapshot only after the status and retry counter commit.
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishMeasurementMessage(measurementDestination, userDestination, message);
                }
            });
        } else {
            publishMeasurementMessage(measurementDestination, userDestination, message);
        }
    }

    private void publishMeasurementMessage(String measurementDestination, String userDestination,
                                           MeasurementResponseDTO.MeasurementSocketMessageDTO message) {
        messagingTemplate.convertAndSend(measurementDestination, message);
        messagingTemplate.convertAndSend(userDestination, message);
        log.info("Measurement socket message sent. eventType={}, measurementDestination={}, userDestination={}, measurementSessionId={}, status={}, shouldDisconnect={}, photoCaptureAttempt={}, failureReason={}",
                message.getEventType(),
                measurementDestination,
                userDestination,
                message.getMeasurementSessionId(),
                message.getStatus(),
                message.getShouldDisconnect(),
                message.getPhotoCaptureAttempt(),
                message.getFailureReason()
        );
    }

    private String resolveRetryDetail(MeasurementSession measurementSession) {
        if (measurementSession.getPressureRetryDetail() != null) {
            return measurementSession.getPressureRetryDetail();
        }
        return measurementSession.getRecaptureDetail();
    }

    private String resolveStatusMessage(MeasurementStatus status) {
        return switch (status) {
            case WAITING_FOR_PHOTO_ENVIRONMENT -> "사진 촬영 및 온습도 측정을 준비하고 있습니다. 안내에 따라 FSR 센서 판을 올리고 유리판 위에 올라와 주세요.";
            case READY_FOR_PHOTO_ENVIRONMENT -> "사진 촬영 및 온습도 측정 준비가 완료되었습니다. 측정을 시작합니다.";
            case CAPTURING_PHOTO_ENVIRONMENT -> "사진 촬영 및 온습도 측정 중입니다. 잠시 움직이지 말아 주세요.";
            case WAITING_FOR_RECAPTURE -> "사진을 다시 촬영해야 합니다.";
            case READY_FOR_RECAPTURE -> "재촬영 및 온습도 측정 준비가 완료되었습니다. 다시 측정합니다.";
            case WAITING_FOR_PHOTO -> "사진 촬영 준비를 기다리고 있습니다.";
            case READY_FOR_PHOTO -> "사진 촬영 준비가 완료되었습니다. 촬영을 시작합니다.";
            case CAPTURING_PHOTO -> "사진을 촬영하고 있습니다. 잠시 움직이지 말아 주세요.";
            case WAITING_FOR_ENVIRONMENT -> "사진 촬영이 완료되었습니다. 온습도 측정을 위해 기기 가까이 이동해 주세요.";
            case READY_FOR_ENVIRONMENT -> "온습도 측정 준비가 완료되었습니다. 측정을 시작합니다.";
            case MEASURING_ENVIRONMENT -> "온습도를 측정하고 있습니다. 잠시 기다려 주세요.";
            case WAITING_FOR_PRESSURE -> "온습도 측정이 완료되었습니다. 내려온 뒤 FSR 센서 판을 내리고 다시 올라와 주세요.";
            case READY_FOR_PRESSURE -> "압력 측정 준비가 완료되었습니다. 측정을 시작합니다.";
            case MEASURING_PRESSURE -> "발 압력을 측정하고 있습니다. 잠시 움직이지 말아 주세요.";
            case WAITING_FOR_PRESSURE_RETRY -> "압력값이 부족합니다. 발 위치를 조정한 뒤 다시 측정해 주세요.";
            case READY_FOR_PRESSURE_RETRY -> "압력 재측정 준비가 완료되었습니다. 다시 측정합니다.";
            case ANALYZING -> "측정이 완료되었습니다. 결과를 분석하고 있습니다.";
            case TRANSFERRING -> "측정이 완료되었습니다. 결과를 분석하고 있습니다.";
            case COMPLETED -> "분석이 완료되었습니다. 결과를 확인해 주세요.";
            case FAILED -> "측정 중 문제가 발생했습니다. 다시 시도해 주세요.";
            case PENDING -> "측정을 준비하고 있습니다.";
            case MEASURING -> "측정 중입니다. 잠시 움직이지 말아 주세요.";
        };
    }
}
