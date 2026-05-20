package com.feetfit.server.web.dto.measurement;

import com.feetfit.server.domain.enums.MeasurementStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class MeasurementResponseDTO {

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateMeasurementSessionResultDTO {
        private Long id;
        private Long deviceId;
        private MeasurementStatus status;
        private LocalDateTime measuredAt;
        private LocalDateTime createdAt;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateMeasurementStatusResultDTO {
        private Long id;
        private MeasurementStatus status;
        private Integer measurementDurationSec;
        private LocalDateTime updatedAt;
    }
}