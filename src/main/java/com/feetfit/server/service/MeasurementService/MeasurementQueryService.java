package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.web.dto.measurement.MeasurementResponseDTO;

public interface MeasurementQueryService {
    MeasurementResponseDTO.TodayMeasurementStatusResultDTO getTodayMeasurementStatus(Long userId);

    MeasurementResponseDTO.WeeklyMeasurementStatusResultDTO getWeeklyMeasurementStatus(Long userId);
}
