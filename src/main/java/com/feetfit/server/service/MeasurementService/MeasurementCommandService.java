package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.web.dto.measurement.MeasurementRequestDTO;
import com.feetfit.server.web.dto.measurement.MeasurementResponseDTO;

public interface MeasurementCommandService {
    MeasurementResponseDTO.CreateMeasurementSessionResultDTO createMeasurementSession(Long userId);

    MeasurementResponseDTO.UpdateMeasurementStatusResultDTO updateMeasurementStatus(
            Long userId, Long measurementSessionId, MeasurementRequestDTO.UpdateMeasurementStatusDTO request);
}
