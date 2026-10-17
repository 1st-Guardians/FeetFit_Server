package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.DeviceHandler;
import com.feetfit.server.apiPayload.exception.handler.MeasurementHandler;
import com.feetfit.server.apiPayload.exception.handler.UserHandler;
import com.feetfit.server.converter.MeasurementConverter;
import com.feetfit.server.domain.Device;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.enums.ConnectionStatus;
import com.feetfit.server.domain.enums.MeasurementFailureReason;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.repository.MeasurementSessionRepository;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.web.dto.measurement.MeasurementRequestDTO;
import com.feetfit.server.web.dto.measurement.MeasurementResponseDTO;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@Transactional
@RequiredArgsConstructor
public class MeasurementCommandServiceImpl implements MeasurementCommandService {

    private static final String RECAPTURE_DETAIL =
            "발이 기준 ArUco 마커를 가리고 있습니다. 마커가 모두 보이도록 발 위치를 조정한 뒤 다시 촬영해 주세요.";
    private static final String RECAPTURE_EXHAUSTED_DETAIL =
            "기준 ArUco 마커를 반복적으로 인식하지 못했습니다. 재촬영 3회를 모두 사용했습니다.";
    private static final String PRESSURE_RETRY_DETAIL =
            "압력값이 부족하거나 유효하지 않습니다. 발 위치를 조정한 뒤 다시 측정해 주세요.";
    private static final String PRESSURE_RETRY_EXHAUSTED_DETAIL =
            "압력값을 반복적으로 정상 측정하지 못했습니다. 압력 재측정 3회를 모두 사용했습니다.";

    private final MeasurementSessionRepository measurementSessionRepository;
    private final UserRepository userRepository;
    private final MeasurementSocketService measurementSocketService;
    private final MeasurementHardwareClient measurementHardwareClient;
    private final MeasurementCompletionService measurementCompletionService;
    private final EntityManager entityManager;

    @Override
    public MeasurementResponseDTO.CreateMeasurementSessionResultDTO createMeasurementSession(Long userId, String authorizationHeader) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));

        // 연결된 디바이스 없는 경우
        Device device = user.getDevice();
        if (device == null) {
            throw new DeviceHandler(ErrorStatus.DEVICE_NOT_FOUND);
        }
        if (device.getConnectionStatus() != ConnectionStatus.CONNECTED) {
            throw new DeviceHandler(ErrorStatus.DEVICE_NOT_CONNECTED);
        }

        MeasurementSession saved = measurementSessionRepository.save(
                MeasurementConverter.toMeasurementSession(user, device)
        );
        saved.updateStatus(MeasurementStatus.WAITING_FOR_PHOTO_ENVIRONMENT, null);
        measurementCompletionService.initialize(saved);

        measurementSocketService.sendMeasurementStatusChanged(saved);
        requestHardwareTaskAfterCommit(() -> measurementHardwareClient.requestMeasurementStart(
                saved.getId(), authorizationHeader));

        return MeasurementConverter.toCreateMeasurementSessionResultDTO(saved);
    }

    @Override
    public MeasurementResponseDTO.UpdateMeasurementStatusResultDTO updateMeasurementStatus(
            Long userId,
            Long measurementSessionId,
            MeasurementRequestDTO.UpdateMeasurementStatusDTO request,
            String authorizationHeader) {

        MeasurementSession measurementSession = getOwnedMeasurementSession(userId, measurementSessionId);
        MeasurementStatus previousStatus = measurementSession.getStatus();
        if (request.getStatus() == null
                || (request.getMeasurementDurationSec() != null && request.getMeasurementDurationSec() <= 0)) {
            throw new MeasurementHandler(ErrorStatus._BAD_REQUEST);
        }

        if (previousStatus == MeasurementStatus.FAILED) {
            measurementSocketService.sendMeasurementStatusChanged(measurementSession);
            if (request.getStatus() == MeasurementStatus.FAILED) {
                return MeasurementConverter.toUpdateMeasurementStatusResultDTO(measurementSession);
            }
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_ALREADY_FAILED);
        }

        if (previousStatus == MeasurementStatus.COMPLETED && request.getStatus() != MeasurementStatus.COMPLETED) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_INVALID_STATUS_TRANSITION);
        }
        MeasurementStatus nextStatus = normalizeIncomingStatus(request.getStatus());
        validatePhotoCaptureAttempt(measurementSession, request, nextStatus);
        validatePressureMeasurementAttempt(measurementSession, request, nextStatus);

        if (nextStatus == MeasurementStatus.WAITING_FOR_RECAPTURE) {
            requirePhotoRecapture(measurementSession, request);
            return MeasurementConverter.toUpdateMeasurementStatusResultDTO(measurementSession);
        }

        if (nextStatus == MeasurementStatus.WAITING_FOR_PRESSURE_RETRY) {
            requirePressureRetry(measurementSession, request);
            return MeasurementConverter.toUpdateMeasurementStatusResultDTO(measurementSession);
        }

        validateRecaptureTransition(measurementSession, nextStatus);
        validatePressureRetryTransition(measurementSession, nextStatus);

        if (nextStatus == MeasurementStatus.COMPLETED) {
            measurementCompletionService.completeMeasurementIfReady(
                    measurementSession,
                    request.getMeasurementDurationSec()
            );
            return MeasurementConverter.toUpdateMeasurementStatusResultDTO(measurementSession);
        }

        if (nextStatus == MeasurementStatus.FAILED) {
            failMeasurement(
                    measurementSession,
                    request.getMeasurementDurationSec(),
                    request.getFailureReason(),
                    request.getFailureDetail()
            );
            return MeasurementConverter.toUpdateMeasurementStatusResultDTO(measurementSession);
        }

        if (nextStatus == MeasurementStatus.READY_FOR_RECAPTURE) {
            if (previousStatus == MeasurementStatus.READY_FOR_RECAPTURE) {
                return MeasurementConverter.toUpdateMeasurementStatusResultDTO(measurementSession);
            }
            if (measurementSession.getRemainingPhotoRecaptures() == 0) {
                throw new MeasurementHandler(ErrorStatus.MEASUREMENT_INVALID_STATUS_TRANSITION);
            }
            measurementSession.startPhotoRecapture();
        }
        if (nextStatus == MeasurementStatus.READY_FOR_PRESSURE_RETRY) {
            if (previousStatus == MeasurementStatus.READY_FOR_PRESSURE_RETRY) {
                return MeasurementConverter.toUpdateMeasurementStatusResultDTO(measurementSession);
            }
            if (measurementSession.getRemainingPressureRetries() == 0) {
                throw new MeasurementHandler(ErrorStatus.MEASUREMENT_INVALID_STATUS_TRANSITION);
            }
            measurementSession.startPressureRetry();
        }
        measurementSession.updateStatus(nextStatus, request.getMeasurementDurationSec());
        measurementSession.clearFailure();
        measurementCompletionService.refreshCaptureCompletedByStatus(measurementSession, nextStatus);
        if (measurementSession.getStatus() != MeasurementStatus.COMPLETED
                && shouldPublishStatusChanged(nextStatus)) {
            measurementSocketService.sendMeasurementStatusChanged(measurementSession);
        }
        requestHardwareTaskIfNeeded(measurementSession, previousStatus, authorizationHeader);
        return MeasurementConverter.toUpdateMeasurementStatusResultDTO(measurementSession);
    }

    private MeasurementStatus normalizeIncomingStatus(MeasurementStatus status) {
        return switch (status) {
            case READY_FOR_PHOTO -> MeasurementStatus.READY_FOR_PHOTO_ENVIRONMENT;
            case CAPTURING_PHOTO -> MeasurementStatus.CAPTURING_PHOTO_ENVIRONMENT;
            default -> status;
        };
    }

    private boolean shouldPublishStatusChanged(MeasurementStatus status) {
        return status != MeasurementStatus.WAITING_FOR_ENVIRONMENT
                && status != MeasurementStatus.READY_FOR_ENVIRONMENT
                && status != MeasurementStatus.MEASURING_ENVIRONMENT;
    }

    private void requestHardwareTaskIfNeeded(
            MeasurementSession measurementSession,
            MeasurementStatus previousStatus,
            String authorizationHeader) {
        MeasurementStatus currentStatus = measurementSession.getStatus();
        if (previousStatus == currentStatus) {
            return;
        }

        Runnable hardwareRequest = switch (currentStatus) {
            case READY_FOR_PHOTO_ENVIRONMENT -> () -> measurementHardwareClient.requestPhotoCapture(
                    measurementSession.getId(), authorizationHeader);
            case READY_FOR_RECAPTURE -> {
                int attempt = measurementSession.getPhotoRecaptureCount();
                yield () -> measurementHardwareClient.requestPhotoCapture(
                        measurementSession.getId(), authorizationHeader, attempt);
            }
            case WAITING_FOR_ENVIRONMENT, READY_FOR_ENVIRONMENT -> () ->
                    measurementHardwareClient.requestEnvironmentMeasurement(measurementSession.getId(), authorizationHeader);
            case READY_FOR_PRESSURE -> () -> measurementHardwareClient.requestPressureMeasurement(
                    measurementSession.getId(), authorizationHeader);
            case READY_FOR_PRESSURE_RETRY -> {
                int attempt = measurementSession.getPressureRetryCount();
                yield () -> measurementHardwareClient.requestPressureMeasurement(
                        measurementSession.getId(), authorizationHeader, attempt);
            }
            default -> null;
        };
        if (hardwareRequest == null) {
            return;
        }

        requestHardwareTaskAfterCommit(hardwareRequest);
    }

    private void requestHardwareTaskAfterCommit(Runnable hardwareRequest) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                hardwareRequest.run();
            }
        });
    }

    private boolean isPhotoCaptureStage(MeasurementStatus status) {
        return status == MeasurementStatus.READY_FOR_PHOTO_ENVIRONMENT
                || status == MeasurementStatus.CAPTURING_PHOTO_ENVIRONMENT
                || status == MeasurementStatus.WAITING_FOR_RECAPTURE
                || status == MeasurementStatus.READY_FOR_RECAPTURE
                || status == MeasurementStatus.READY_FOR_PHOTO
                || status == MeasurementStatus.CAPTURING_PHOTO;
    }

    private void validatePhotoCaptureAttempt(MeasurementSession session,
                                             MeasurementRequestDTO.UpdateMeasurementStatusDTO request,
                                             MeasurementStatus nextStatus) {
        Integer attempt = request.getPhotoCaptureAttempt();
        if (attempt != null && attempt < 0) {
            throw new MeasurementHandler(ErrorStatus._BAD_REQUEST);
        }
        boolean photoCallback = nextStatus == MeasurementStatus.CAPTURING_PHOTO_ENVIRONMENT
                || nextStatus == MeasurementStatus.WAITING_FOR_RECAPTURE
                || nextStatus == MeasurementStatus.WAITING_FOR_ENVIRONMENT
                || (nextStatus == MeasurementStatus.FAILED
                    && (request.getFailureReason() == MeasurementFailureReason.INVALID_CAPTURE_DATA
                        || request.getFailureReason() == MeasurementFailureReason.CAMERA_ERROR));
        if ((attempt != null && attempt != session.getPhotoRecaptureCount())
                || (photoCallback && session.getPhotoRecaptureCount() > 0 && attempt == null)) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_STALE_PHOTO_CAPTURE);
        }
    }

    private void validatePressureMeasurementAttempt(MeasurementSession session,
                                                    MeasurementRequestDTO.UpdateMeasurementStatusDTO request,
                                                    MeasurementStatus nextStatus) {
        Integer attempt = request.getPressureMeasurementAttempt();
        if (attempt != null && attempt < 0) {
            throw new MeasurementHandler(ErrorStatus._BAD_REQUEST);
        }
        boolean pressureCallback = nextStatus == MeasurementStatus.MEASURING_PRESSURE
                || nextStatus == MeasurementStatus.WAITING_FOR_PRESSURE_RETRY
                || nextStatus == MeasurementStatus.ANALYZING
                || (nextStatus == MeasurementStatus.FAILED
                    && (request.getFailureReason() == MeasurementFailureReason.INVALID_PRESSURE_DATA
                        || request.getFailureReason() == MeasurementFailureReason.PRESSURE_SENSOR_ERROR));
        if ((attempt != null && attempt != session.getPressureRetryCount())
                || (pressureCallback && session.getPressureRetryCount() > 0 && attempt == null)) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_STALE_PHOTO_CAPTURE);
        }
    }

    private void validateRecaptureTransition(MeasurementSession session, MeasurementStatus next) {
        MeasurementStatus current = session.getStatus();
        if (next == MeasurementStatus.FAILED) {
            return;
        }
        boolean invalid = (next == MeasurementStatus.READY_FOR_RECAPTURE
                && current != MeasurementStatus.WAITING_FOR_RECAPTURE && current != next)
                || (current == MeasurementStatus.WAITING_FOR_RECAPTURE && next != MeasurementStatus.READY_FOR_RECAPTURE)
                || (current == MeasurementStatus.READY_FOR_RECAPTURE
                    && next != current && next != MeasurementStatus.CAPTURING_PHOTO_ENVIRONMENT)
                || (session.getPhotoRecaptureCount() > 0 && next == MeasurementStatus.READY_FOR_PHOTO_ENVIRONMENT)
                || (session.getPhotoRecaptureCount() > 0 && next == MeasurementStatus.READY_FOR_PHOTO)
                || (session.getPhotoRecaptureCount() > 0 && current == MeasurementStatus.CAPTURING_PHOTO_ENVIRONMENT
                    && next != current
                    && next != MeasurementStatus.WAITING_FOR_ENVIRONMENT)
                || (session.getPhotoRecaptureCount() > 0 && next == MeasurementStatus.CAPTURING_PHOTO_ENVIRONMENT
                    && current != next && current != MeasurementStatus.READY_FOR_RECAPTURE)
                || (session.getPhotoRecaptureCount() > 0 && next == MeasurementStatus.WAITING_FOR_PRESSURE
                    && current != next
                    && !isInternalEnvironmentStatus(current));
        if (invalid) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_INVALID_STATUS_TRANSITION);
        }
    }

    private boolean isInternalEnvironmentStatus(MeasurementStatus status) {
        return status == MeasurementStatus.WAITING_FOR_ENVIRONMENT
                || status == MeasurementStatus.READY_FOR_ENVIRONMENT
                || status == MeasurementStatus.MEASURING_ENVIRONMENT;
    }

    private void validatePressureRetryTransition(MeasurementSession session, MeasurementStatus next) {
        MeasurementStatus current = session.getStatus();
        if (next == MeasurementStatus.FAILED) {
            return;
        }
        boolean invalid = (next == MeasurementStatus.READY_FOR_PRESSURE_RETRY
                && current != MeasurementStatus.WAITING_FOR_PRESSURE_RETRY && current != next)
                || (current == MeasurementStatus.WAITING_FOR_PRESSURE_RETRY
                    && next != current && next != MeasurementStatus.READY_FOR_PRESSURE_RETRY)
                || (current == MeasurementStatus.READY_FOR_PRESSURE_RETRY
                    && next != current && next != MeasurementStatus.MEASURING_PRESSURE)
                || (session.getPressureRetryCount() > 0 && next == MeasurementStatus.READY_FOR_PRESSURE)
                || (session.getPressureRetryCount() > 0 && next == MeasurementStatus.MEASURING_PRESSURE
                    && current != next && current != MeasurementStatus.READY_FOR_PRESSURE_RETRY)
                || (session.getPressureRetryCount() > 0 && current == MeasurementStatus.MEASURING_PRESSURE
                    && next != current
                    && next != MeasurementStatus.WAITING_FOR_PRESSURE_RETRY
                    && next != MeasurementStatus.ANALYZING);
        if (invalid) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_INVALID_STATUS_TRANSITION);
        }
    }

    private void requirePhotoRecapture(MeasurementSession session,
                                      MeasurementRequestDTO.UpdateMeasurementStatusDTO request) {
        if (request.getFailureReason() != null
                && request.getFailureReason() != MeasurementFailureReason.INVALID_CAPTURE_DATA) {
            throw new MeasurementHandler(ErrorStatus._BAD_REQUEST);
        }
        if (!isPhotoCaptureStage(session.getStatus())) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_INVALID_STATUS_TRANSITION);
        }
        if (session.getStatus() == MeasurementStatus.WAITING_FOR_RECAPTURE) {
            return;
        }
        if (session.getRemainingPhotoRecaptures() == 0) {
            failMeasurement(session, request.getMeasurementDurationSec(),
                    MeasurementFailureReason.INVALID_CAPTURE_DATA, RECAPTURE_EXHAUSTED_DETAIL);
            return;
        }
        String detail = request.getFailureDetail();
        session.requirePhotoRecapture(detail != null && !detail.isBlank() ? detail : RECAPTURE_DETAIL);
        measurementCompletionService.resetPhotoAnalysisForRecapture(session);
        measurementSocketService.sendMeasurementStatusChanged(session);
    }

    private boolean isPressureMeasurementStage(MeasurementStatus status) {
        return status == MeasurementStatus.READY_FOR_PRESSURE
                || status == MeasurementStatus.MEASURING_PRESSURE
                || status == MeasurementStatus.ANALYZING
                || status == MeasurementStatus.WAITING_FOR_PRESSURE_RETRY
                || status == MeasurementStatus.READY_FOR_PRESSURE_RETRY;
    }

    private void requirePressureRetry(MeasurementSession session,
                                      MeasurementRequestDTO.UpdateMeasurementStatusDTO request) {
        if (request.getFailureReason() != null
                && request.getFailureReason() != MeasurementFailureReason.INVALID_PRESSURE_DATA) {
            throw new MeasurementHandler(ErrorStatus._BAD_REQUEST);
        }
        if (!isPressureMeasurementStage(session.getStatus())) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_INVALID_STATUS_TRANSITION);
        }
        if (session.getStatus() == MeasurementStatus.WAITING_FOR_PRESSURE_RETRY) {
            return;
        }
        if (session.getRemainingPressureRetries() == 0) {
            failMeasurement(session, request.getMeasurementDurationSec(),
                    MeasurementFailureReason.INVALID_PRESSURE_DATA, PRESSURE_RETRY_EXHAUSTED_DETAIL);
            return;
        }
        String detail = request.getFailureDetail();
        session.requirePressureRetry(detail != null && !detail.isBlank() ? detail : PRESSURE_RETRY_DETAIL);
        measurementCompletionService.resetPressureAnalysisForRetry(session);
        measurementSocketService.sendMeasurementStatusChanged(session);
    }

    @Override
    public MeasurementResponseDTO.DeleteMeasurementRecordsResultDTO deleteMeasurementRecords(Long userId, Long measurementSessionId) {
        getOwnedMeasurementSession(userId, measurementSessionId);

        int deletedShoeRecommendationReasonReviewCount = executeMeasurementDelete("""
                DELETE FROM shoe_recommendation_reason_review
                WHERE reason_id IN (
                    SELECT id FROM shoe_recommendation_reason
                    WHERE shoe_recommendation_id IN (
                        SELECT id FROM shoe_recommendation
                        WHERE measurement_session_id = :measurementSessionId
                    )
                )
                """, measurementSessionId);
        int deletedShoeRecommendationReasonCount = executeMeasurementDelete("""
                DELETE FROM shoe_recommendation_reason
                WHERE shoe_recommendation_id IN (
                    SELECT id FROM shoe_recommendation
                    WHERE measurement_session_id = :measurementSessionId
                )
                """, measurementSessionId);
        int deletedShoeRecommendationCount = executeMeasurementDelete("""
                DELETE FROM shoe_recommendation
                WHERE measurement_session_id = :measurementSessionId
                """, measurementSessionId);
        int deletedShoeRecommendationRunCount = executeMeasurementDelete("""
                DELETE FROM shoe_recommendation_run
                WHERE measurement_session_id = :measurementSessionId
                """, measurementSessionId);
        executeMeasurementDelete("""
                DELETE FROM drug_recommendation
                WHERE report_id IN (
                    SELECT id FROM report WHERE measurement_session_id = :measurementSessionId
                )
                """, measurementSessionId);
        int deletedMetricAnalysisResultCount = executeMeasurementDelete("""
                DELETE FROM metric_analysis_result
                WHERE report_id IN (
                    SELECT id FROM report WHERE measurement_session_id = :measurementSessionId
                )
                """, measurementSessionId);
        int deletedReportCount = executeMeasurementDelete("""
                DELETE FROM report
                WHERE measurement_session_id = :measurementSessionId
                """, measurementSessionId);
        int deletedHalluxValgusAnalysisCount = executeMeasurementDelete("""
                DELETE FROM hallux_valgus_analysis
                WHERE measurement_session_id = :measurementSessionId
                """, measurementSessionId);
        int deletedTinaPedisAnalysisCount = executeMeasurementDelete("""
                DELETE FROM tina_pedis_analyses
                WHERE measurement_id = :measurementSessionId
                """, measurementSessionId);
        int deletedDailyFootAnalysisCount = executeMeasurementDelete("""
                DELETE FROM daily_foot_analysis
                WHERE measurement_id = :measurementSessionId
                """, measurementSessionId);
        int deletedPlantarFootprintCount = executeMeasurementDelete("""
                DELETE FROM plantar_footprint
                WHERE measurement_id = :measurementSessionId
                """, measurementSessionId);
        int deletedStaticPressureAnalysisCount = executeMeasurementDelete("""
                DELETE FROM static_pressure_analysis
                WHERE measurement_session_id = :measurementSessionId
                """, measurementSessionId);
        int deletedFootEnvironmentAnalysisCount = executeMeasurementDelete("""
                DELETE FROM foot_environment_analysis
                WHERE measurement_session_id = :measurementSessionId
                """, measurementSessionId);
        int deletedFootOdorAnalysisCount = executeMeasurementDelete("""
                DELETE FROM foot_odor_analysis
                WHERE measurement_session_id = :measurementSessionId
                """, measurementSessionId);
        int deletedPressureSensorValueCount = executeMeasurementDelete("""
                DELETE FROM pressure_sensor_value
                WHERE pressure_sensor_reading_id IN (
                    SELECT id FROM pressure_sensor_reading WHERE measurement_session_id = :measurementSessionId
                )
                """, measurementSessionId);
        int deletedPressureSensorReadingCount = executeMeasurementDelete("""
                DELETE FROM pressure_sensor_reading
                WHERE measurement_session_id = :measurementSessionId
                """, measurementSessionId);
        int deletedFootEnvironmentReadingCount = executeMeasurementDelete("""
                DELETE FROM foot_environment_reading
                WHERE measurement_session_id = :measurementSessionId
                """, measurementSessionId);
        int deletedFootOdorReadingCount = executeMeasurementDelete("""
                DELETE FROM foot_odor_reading
                WHERE measurement_session_id = :measurementSessionId
                """, measurementSessionId);
        int deletedMeasurementAnalysisStatusCount = executeMeasurementDelete("""
                DELETE FROM measurement_analysis_status
                WHERE measurement_session_id = :measurementSessionId
                """, measurementSessionId);
        int deletedMeasurementSessionCount = executeMeasurementDelete("""
                DELETE FROM measurement_session
                WHERE id = :measurementSessionId
                """, measurementSessionId);

        return MeasurementResponseDTO.DeleteMeasurementRecordsResultDTO.builder()
                .measurementSessionId(measurementSessionId)
                .deletedShoeRecommendationReasonReviewCount(deletedShoeRecommendationReasonReviewCount)
                .deletedShoeRecommendationReasonCount(deletedShoeRecommendationReasonCount)
                .deletedShoeRecommendationCount(deletedShoeRecommendationCount)
                .deletedShoeRecommendationRunCount(deletedShoeRecommendationRunCount)
                .deletedMetricAnalysisResultCount(deletedMetricAnalysisResultCount)
                .deletedReportCount(deletedReportCount)
                .deletedHalluxValgusAnalysisCount(deletedHalluxValgusAnalysisCount)
                .deletedTinaPedisAnalysisCount(deletedTinaPedisAnalysisCount)
                .deletedDailyFootAnalysisCount(deletedDailyFootAnalysisCount)
                .deletedPlantarFootprintCount(deletedPlantarFootprintCount)
                .deletedStaticPressureAnalysisCount(deletedStaticPressureAnalysisCount)
                .deletedFootEnvironmentAnalysisCount(deletedFootEnvironmentAnalysisCount)
                .deletedFootOdorAnalysisCount(deletedFootOdorAnalysisCount)
                .deletedPressureSensorValueCount(deletedPressureSensorValueCount)
                .deletedPressureSensorReadingCount(deletedPressureSensorReadingCount)
                .deletedFootEnvironmentReadingCount(deletedFootEnvironmentReadingCount)
                .deletedFootOdorReadingCount(deletedFootOdorReadingCount)
                .deletedMeasurementAnalysisStatusCount(deletedMeasurementAnalysisStatusCount)
                .deletedMeasurementSessionCount(deletedMeasurementSessionCount)
                .build();
    }

    private int executeMeasurementDelete(String sql, Long measurementSessionId) {
        return entityManager.createNativeQuery(sql)
                .setParameter("measurementSessionId", measurementSessionId)
                .executeUpdate();
    }

    private MeasurementSession getOwnedMeasurementSession(Long userId, Long measurementSessionId) {
        MeasurementSession measurementSession = measurementSessionRepository
                .findByIdForUpdate(measurementSessionId)
                .orElseThrow(() -> new MeasurementHandler(ErrorStatus.MEASUREMENT_NOT_FOUND));

        if (!measurementSession.getUser().getId().equals(userId)) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_FORBIDDEN);
        }

        return measurementSession;
    }

    private void failMeasurement(
            MeasurementSession measurementSession,
            Integer measurementDurationSec,
            MeasurementFailureReason failureReason,
            String failureDetail) {
        measurementSession.clearRecaptureDetail();
        measurementSession.clearPressureRetryDetail();
        measurementSession.updateStatus(
                MeasurementStatus.FAILED,
                resolveMeasurementDurationSec(measurementSession, measurementDurationSec)
        );
        measurementSession.updateFailure(
                failureReason != null ? failureReason : MeasurementFailureReason.UNKNOWN,
                failureDetail
        );
        measurementSocketService.sendMeasurementStatusChanged(measurementSession);
    }

    private Integer resolveMeasurementDurationSec(MeasurementSession measurementSession, Integer requestedDurationSec) {
        if (requestedDurationSec != null) {
            if (requestedDurationSec <= 0) {
                throw new MeasurementHandler(ErrorStatus._BAD_REQUEST);
            }
            return requestedDurationSec;
        }

        if (measurementSession.getMeasurementDurationSec() != null && measurementSession.getMeasurementDurationSec() > 0) {
            return measurementSession.getMeasurementDurationSec();
        }

        long measuredSeconds = Duration.between(measurementSession.getMeasuredAt(), LocalDateTime.now()).getSeconds();
        return (int) Math.max(1L, Math.min(measuredSeconds, Integer.MAX_VALUE));
    }
}
