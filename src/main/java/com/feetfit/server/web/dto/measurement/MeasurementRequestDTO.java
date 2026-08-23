package com.feetfit.server.web.dto.measurement;

import com.feetfit.server.domain.enums.MeasurementFailureReason;
import com.feetfit.server.domain.enums.MeasurementStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class MeasurementRequestDTO {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "측정 세션 상태 수정 요청")
    public static class UpdateMeasurementStatusDTO {

        @Schema(description = "측정 상태", example = "READY_FOR_PHOTO", allowableValues = {"WAITING_FOR_PHOTO", "READY_FOR_PHOTO", "CAPTURING_PHOTO", "WAITING_FOR_PRESSURE", "READY_FOR_PRESSURE", "MEASURING_PRESSURE", "ANALYZING", "COMPLETED", "FAILED", "PENDING", "MEASURING", "TRANSFERRING"})
        @NotNull(message = "status(측정 상태)는 필수입니다.")
        private MeasurementStatus status;

        @Schema(description = "측정 소요 시간(초)", example = "180")
        private Integer measurementDurationSec;

        @Schema(description = "측정 실패 원인. status=FAILED일 때 사용", example = "CAMERA_ERROR", allowableValues = {"CAMERA_ERROR", "PRESSURE_SENSOR_ERROR", "AI_SERVER_ERROR", "HARDWARE_TIMEOUT", "NETWORK_ERROR", "USER_CANCELLED", "UNKNOWN"})
        private MeasurementFailureReason failureReason;

        @Schema(description = "측정 실패 상세 설명. status=FAILED일 때 사용", example = "Camera timeout")
        private String failureDetail;

        public UpdateMeasurementStatusDTO(MeasurementStatus status, Integer measurementDurationSec) {
            this.status = status;
            this.measurementDurationSec = measurementDurationSec;
        }
    }
}
