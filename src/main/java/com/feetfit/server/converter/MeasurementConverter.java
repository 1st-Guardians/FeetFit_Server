package com.feetfit.server.converter;

import com.feetfit.server.domain.Device;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.User;
import com.feetfit.server.web.dto.measurement.MeasurementResponseDTO;

import java.time.LocalDateTime;

public class MeasurementConverter {

    public static MeasurementSession toMeasurementSession(User user, Device device) {
        return MeasurementSession.builder()
                .user(user)
                .device(device)
                .measuredAt(LocalDateTime.now())
                .build();
    }

    public static MeasurementResponseDTO.CreateMeasurementSessionResultDTO toCreateMeasurementSessionResultDTO(
            MeasurementSession measurementSession) {
        return MeasurementResponseDTO.CreateMeasurementSessionResultDTO.builder()
                .id(measurementSession.getId())
                .deviceId(measurementSession.getDevice().getId())
                .status(measurementSession.getStatus())
                .measuredAt(measurementSession.getMeasuredAt())
                .createdAt(measurementSession.getCreatedAt())
                .build();
    }

    public static MeasurementResponseDTO.UpdateMeasurementStatusResultDTO toUpdateMeasurementStatusResultDTO(
            MeasurementSession measurementSession) {
        return MeasurementResponseDTO.UpdateMeasurementStatusResultDTO.builder()
                .id(measurementSession.getId())
                .status(measurementSession.getStatus())
                .measurementDurationSec(measurementSession.getMeasurementDurationSec())
                .updatedAt(measurementSession.getUpdatedAt())
                .build();
    }
}