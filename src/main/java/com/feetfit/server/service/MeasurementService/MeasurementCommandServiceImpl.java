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
import com.feetfit.server.repository.HalluxValgusAnalysisRepository;
import com.feetfit.server.repository.MeasurementSessionRepository;
import com.feetfit.server.repository.TinaPedisAnalysisRepository;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.web.dto.measurement.MeasurementRequestDTO;
import com.feetfit.server.web.dto.measurement.MeasurementResponseDTO;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final TinaPedisAnalysisRepository tinaPedisAnalysisRepository;
    private final HalluxValgusAnalysisRepository halluxValgusAnalysisRepository;
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

        measurementSocketService.sendMeasurementStatusChanged(saved);
        measurementHardwareClient.requestMeasurementStart(saved.getId(), authorizationHeader);

        return MeasurementConverter.toCreateMeasurementSessionResultDTO(saved);
    }

    @Override
    public MeasurementResponseDTO.UpdateMeasurementStatusResultDTO updateMeasurementStatus(
            Long userId, Long measurementSessionId, MeasurementRequestDTO.UpdateMeasurementStatusDTO request) {

        MeasurementSession measurementSession = getOwnedMeasurementSession(userId, measurementSessionId);

        if (request.getStatus() == MeasurementStatus.COMPLETED) {
            completeMeasurement(measurementSession, request.getMeasurementDurationSec());
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
        measurementSocketService.sendMeasurementStatusChanged(measurementSession);
        return MeasurementConverter.toUpdateMeasurementStatusResultDTO(measurementSession);
    }

    @Override
    public MeasurementResponseDTO.DeleteMeasurementRecordsResultDTO deleteMeasurementRecords(Long measurementSessionId) {
        if (!measurementSessionRepository.existsById(measurementSessionId)) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_NOT_FOUND);
        }

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
        int deletedMeasurementSessionCount = executeMeasurementDelete("""
                DELETE FROM measurement_session
                WHERE id = :measurementSessionId
                """, measurementSessionId);

        return MeasurementResponseDTO.DeleteMeasurementRecordsResultDTO.builder()
                .measurementSessionId(measurementSessionId)
                .deletedMetricAnalysisResultCount(deletedMetricAnalysisResultCount)
                .deletedReportCount(deletedReportCount)
                .deletedHalluxValgusAnalysisCount(deletedHalluxValgusAnalysisCount)
                .deletedTinaPedisAnalysisCount(deletedTinaPedisAnalysisCount)
                .deletedDailyFootAnalysisCount(deletedDailyFootAnalysisCount)
                .deletedPlantarFootprintCount(deletedPlantarFootprintCount)
                .deletedStaticPressureAnalysisCount(deletedStaticPressureAnalysisCount)
                .deletedFootEnvironmentAnalysisCount(deletedFootEnvironmentAnalysisCount)
                .deletedFootOdorAnalysisCount(deletedFootOdorAnalysisCount)
                .deletedPressureSensorReadingCount(deletedPressureSensorReadingCount)
                .deletedFootEnvironmentReadingCount(deletedFootEnvironmentReadingCount)
                .deletedFootOdorReadingCount(deletedFootOdorReadingCount)
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
                .findById(measurementSessionId)
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

    private void completeMeasurement(MeasurementSession measurementSession, Integer measurementDurationSec) {
        boolean hasHalluxValgus = halluxValgusAnalysisRepository
                .existsByMeasurementSessionId(measurementSession.getId());
        boolean hasTinaPedis = tinaPedisAnalysisRepository
                .existsByMeasurementSessionId(measurementSession.getId());

        if (!hasHalluxValgus || !hasTinaPedis) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_ANALYSIS_NOT_READY);
        }

        boolean wasCompleted = measurementSession.getStatus() == MeasurementStatus.COMPLETED;
        measurementSession.updateStatus(
                MeasurementStatus.COMPLETED,
                resolveMeasurementDurationSec(measurementSession, measurementDurationSec)
        );
        measurementSession.clearFailure();

        if (!wasCompleted) {
            measurementSocketService.sendMeasurementStatusChanged(measurementSession);
        }
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
