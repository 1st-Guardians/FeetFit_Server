package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.enums.MeasurementFailureReason;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.MetricType;
import com.feetfit.server.event.MeasurementHardwareFailureEvent;
import com.feetfit.server.event.MeasurementPressureStatusFailureEvent;
import com.feetfit.server.repository.MeasurementSessionRepository;
import com.feetfit.server.web.dto.measurement.MeasurementRequestDTO;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeasurementPressureRecoveryService {
    private static final Set<MeasurementFailureReason> RECOVERABLE_REASONS = EnumSet.of(
            MeasurementFailureReason.INVALID_PRESSURE_DATA,
            MeasurementFailureReason.PRESSURE_SENSOR_ERROR,
            MeasurementFailureReason.AI_SERVER_ERROR,
            MeasurementFailureReason.HARDWARE_TIMEOUT,
            MeasurementFailureReason.NETWORK_ERROR,
            MeasurementFailureReason.REPORT_SAVE_ERROR);

    private final MeasurementSessionRepository sessions;
    private final MeasurementCommandService commands;
    private final EntityManager entityManager;
    private final MeasurementSocketService sockets;

    public static boolean isRecoverableFailure(MeasurementFailureReason reason) {
        return reason != null && RECOVERABLE_REASONS.contains(reason);
    }

    public static boolean isPressureStage(MeasurementStatus status) {
        return status == MeasurementStatus.READY_FOR_PRESSURE
                || status == MeasurementStatus.MEASURING_PRESSURE
                || status == MeasurementStatus.ANALYZING
                || status == MeasurementStatus.WAITING_FOR_PRESSURE_RETRY
                || status == MeasurementStatus.READY_FOR_PRESSURE_RETRY;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void onHardwareFailure(MeasurementHardwareFailureEvent event) {
        recover(null, event.measurementSessionId(), null, event.pressureMeasurementAttempt(), event.reason(),
                "압력 측정 요청을 완료하지 못했어요. 기기 연결 상태를 확인한 뒤 다시 측정해 주세요.", false, null);
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void onStatusFailure(MeasurementPressureStatusFailureEvent event) {
        recover(event.userId(), event.measurementSessionId(), event.photoCaptureAttempt(), event.pressureMeasurementAttempt(),
                MeasurementFailureReason.REPORT_SAVE_ERROR,
                "압력 측정 상태를 저장하지 못했어요. 잠시 후 압력을 다시 측정해 주세요.", true, event.requestedStatus());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void recoverReportFailure(Long userId, Long sessionId, int photoAttempt, int pressureAttempt) {
        recover(userId, sessionId, photoAttempt, pressureAttempt, MeasurementFailureReason.REPORT_SAVE_ERROR,
                "압력 분석 결과를 저장하지 못했어요. 잠시 후 압력을 다시 측정해 주세요. 사진과 온습도 결과는 유지돼요.", true, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void recoverStalledPressure(Long sessionId, int pressureAttempt, MeasurementStatus expectedStatus,
                                       LocalDateTime staleBefore) {
        entityManager.clear();
        MeasurementSession session = sessions.findByIdForUpdate(sessionId).orElse(null);
        if (session == null || session.getPressureRetryCount() != pressureAttempt
                || session.getStatus() != expectedStatus || !isPressureStage(expectedStatus)
                || expectedStatus == MeasurementStatus.WAITING_FOR_PRESSURE_RETRY
                || session.getLastPressureProgressAt() == null
                || session.getLastPressureProgressAt().isAfter(staleBefore)
                || sessions.hasCompletedPressurePipeline(sessionId, MetricType.PRESSURE_BALANCE)) {
            return;
        }
        log.warn("Pressure progress timed out. measurementSessionId={}, pressureMeasurementAttempt={}, status={}, lastProgressAt={}, staleBefore={}",
                sessionId, pressureAttempt, expectedStatus, session.getLastPressureProgressAt(), staleBefore);
        requestRetry(session, expectedStatus == MeasurementStatus.ANALYZING
                        ? MeasurementFailureReason.AI_SERVER_ERROR : MeasurementFailureReason.HARDWARE_TIMEOUT,
                "압력 측정 또는 분석 응답이 지연되고 있어요. 잠시 후 압력을 다시 측정해 주세요. 사진과 온습도 결과는 유지돼요.");
    }

    private void recover(Long userId, Long sessionId, Integer photoAttempt, int pressureAttempt,
                         MeasurementFailureReason reason, String detail, boolean allowStartedWork,
                         MeasurementStatus failedRequest) {
        // Recovery must run after the failed save rolls back, with fresh attempt/state values.
        entityManager.clear();
        MeasurementSession session = sessions.findByIdForUpdate(sessionId).orElse(null);
        if (session == null
                || (userId != null && !session.getUser().getId().equals(userId))
                || session.getPressureRetryCount() != pressureAttempt
                || (photoAttempt != null && session.getPhotoRecaptureCount() != photoAttempt)) {
            return;
        }
        if (failedRequest == MeasurementStatus.READY_FOR_PRESSURE
                && session.getStatus() != MeasurementStatus.WAITING_FOR_PRESSURE) return;
        if (failedRequest == MeasurementStatus.READY_FOR_PRESSURE_RETRY) {
            if (session.getStatus() == MeasurementStatus.WAITING_FOR_PRESSURE_RETRY) {
                session.requirePressureRetry(detail);
                session.updateFailure(reason, detail);
                sockets.sendMeasurementStatusChanged(session);
            }
            return;
        }
        boolean startSaveFailure = failedRequest == MeasurementStatus.READY_FOR_PRESSURE
                && session.getStatus() == MeasurementStatus.WAITING_FOR_PRESSURE;
        if ((!isPressureStage(session.getStatus()) && !startSaveFailure)
                || session.getStatus() == MeasurementStatus.WAITING_FOR_PRESSURE_RETRY) return;
        if (!allowStartedWork && (reason == MeasurementFailureReason.HARDWARE_TIMEOUT
                || session.getStatus() == MeasurementStatus.MEASURING_PRESSURE
                || session.getStatus() == MeasurementStatus.ANALYZING)) {
            log.warn("Pressure request response unconfirmed; waiting for progress before offering retry. measurementSessionId={}, pressureMeasurementAttempt={}, reason={}, status={}",
                    sessionId, pressureAttempt, reason, session.getStatus());
            return;
        }
        requestRetry(session, reason, detail);
    }

    private void requestRetry(MeasurementSession session, MeasurementFailureReason reason, String detail) {
        Long sessionId = session.getId();
        int pressureAttempt = session.getPressureRetryCount();
        commands.updateMeasurementStatus(session.getUser().getId(), sessionId,
                new MeasurementRequestDTO.UpdateMeasurementStatusDTO(
                        MeasurementStatus.WAITING_FOR_PRESSURE_RETRY, null, reason, detail,
                        session.getPhotoRecaptureCount(), pressureAttempt), null);
        log.warn("Pressure retry recovery applied. measurementSessionId={}, pressureMeasurementAttempt={}, reason={}, status={}",
                sessionId, pressureAttempt, reason, session.getStatus());
    }
}
