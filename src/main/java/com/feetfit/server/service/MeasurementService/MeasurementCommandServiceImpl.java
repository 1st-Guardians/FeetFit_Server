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
        saved.updateStatus(MeasurementStatus.WAITING_FOR_PHOTO, null);
        measurementCompletionService.initialize(saved);

        measurementHardwareClient.requestInitialEnvironmentMeasurement(
                saved.getId(),
                authorizationHeader
        );
        measurementSocketService.sendMeasurementStatusChanged(saved);

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

        if (previousStatus == MeasurementStatus.FAILED) {
            measurementSocketService.sendMeasurementStatusChanged(measurementSession);
            if (request.getStatus() == MeasurementStatus.FAILED) {
                return MeasurementConverter.toUpdateMeasurementStatusResultDTO(measurementSession);
            }
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_ALREADY_FAILED);
        }

        if (request.getStatus() == MeasurementStatus.COMPLETED) {
            measurementCompletionService.completeMeasurementIfReady(
                    measurementSession,
                    request.getMeasurementDurationSec()
            );
            return MeasurementConverter.toUpdateMeasurementStatusResultDTO(measurementSession);
        }

        if (request.getStatus() == MeasurementStatus.FAILED) {
            failMeasurement(
                    measurementSession,
                    request.getMeasurementDurationSec(),
                    request.getFailureReason(),
                    request.getFailureDetail()
            );
            return MeasurementConverter.toUpdateMeasurementStatusResultDTO(measurementSession);
        }

        measurementSession.updateStatus(request.getStatus(), request.getMeasurementDurationSec());
        measurementSession.clearFailure();
        measurementCompletionService.refreshCaptureCompletedByStatus(measurementSession, request.getStatus());
        if (measurementSession.getStatus() != MeasurementStatus.COMPLETED) {
            measurementSocketService.sendMeasurementStatusChanged(measurementSession);
        }
        requestHardwareTaskIfNeeded(measurementSession, previousStatus, authorizationHeader);
        return MeasurementConverter.toUpdateMeasurementStatusResultDTO(measurementSession);
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
            case READY_FOR_PHOTO -> () -> measurementHardwareClient.requestPhotoCapture(
                    measurementSession.getId(), authorizationHeader);
            case READY_FOR_ENVIRONMENT -> () -> measurementHardwareClient.requestEnvironmentMeasurement(
                    measurementSession.getId(), authorizationHeader);
            case READY_FOR_PRESSURE -> () -> measurementHardwareClient.requestPressureMeasurement(
                    measurementSession.getId(), authorizationHeader);
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
