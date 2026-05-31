package com.feetfit.server.web.dto.measurement;

import com.feetfit.server.domain.enums.MeasurementStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class MeasurementRequestDTO {

    @Getter
    @NoArgsConstructor
    @Schema(description = "측정 세션 생성 요청")
    public static class CreateMeasurementSessionDTO {

        @Schema(description = "측정에 사용할 디바이스 ID", example = "1")
        @NotNull(message = "디바이스 ID는 필수입니다.")
        private Long deviceId;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "측정 세션 상태 수정 요청")
    public static class UpdateMeasurementStatusDTO {

        @Schema(description = "측정 상태", example = "COMPLETED", allowableValues = {"PENDING", "MEASURING", "TRANSFERRING", "COMPLETED", "FAILED"})
        @NotNull(message = "status(측정 상태)는 필수입니다.")
        private MeasurementStatus status;

        @Schema(description = "측정 소요 시간(초)", example = "180")
        private Integer measurementDurationSec;
    }
}
