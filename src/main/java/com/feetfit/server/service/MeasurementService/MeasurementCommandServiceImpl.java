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
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.repository.HalluxValgusAnalysisRepository;
import com.feetfit.server.repository.MeasurementSessionRepository;
import com.feetfit.server.repository.TinaPedisAnalysisRepository;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.web.dto.measurement.MeasurementRequestDTO;
import com.feetfit.server.web.dto.measurement.MeasurementResponseDTO;
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

        measurementSession.updateStatus(request.getStatus(), request.getMeasurementDurationSec());
        measurementSocketService.sendMeasurementStatusChanged(measurementSession);
        return MeasurementConverter.toUpdateMeasurementStatusResultDTO(measurementSession);
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
