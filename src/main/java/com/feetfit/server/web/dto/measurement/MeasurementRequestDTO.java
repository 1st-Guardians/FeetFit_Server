package com.feetfit.server.web.dto.measurement;

import com.feetfit.server.domain.enums.MeasurementStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class MeasurementRequestDTO {

    @Getter
    @NoArgsConstructor
    public static class CreateMeasurementSessionDTO {

        @NotNull(message = "디바이스 ID는 필수입니다.")
        private Long deviceId;
    }

    @Getter
    @NoArgsConstructor
    public static class UpdateMeasurementStatusDTO {

        @NotNull(message = "측정 상태는 필수입니다.")
        private MeasurementStatus status;

        private Integer measurementDurationSec;
    }
}