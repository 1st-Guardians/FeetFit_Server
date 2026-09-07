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

        @Schema(description = "측정 상태", example = "READY_FOR_PHOTO")
        @NotNull(message = "status(측정 상태)는 필수입니다.")
        private MeasurementStatus status;

        @Schema(description = "측정 소요 시간(초)", example = "180")
        private Integer measurementDurationSec;

        @Schema(description = "측정 실패 원인. FAILED는 최종 실패이며, 발에 의한 ArUco 마커 가림은 WAITING_FOR_RECAPTURE 상태로 요청해야 합니다.", example = "INVALID_CAPTURE_DATA")
        private MeasurementFailureReason failureReason;

        @Schema(description = "실패 또는 재촬영 사유. WAITING_FOR_RECAPTURE에서는 발에 의한 ArUco 마커 가림 안내에 사용합니다.", example = "발이 기준 ArUco 마커를 가리고 있습니다. 발 위치를 조정한 뒤 다시 촬영해 주세요.")
        private String failureDetail;

        @Schema(description = "촬영 회차. 최초 촬영은 0이며 생략 가능, 재촬영 콜백에는 하드웨어 요청으로 받은 값을 반드시 전달합니다.", example = "1")
        private Integer photoCaptureAttempt;

        public UpdateMeasurementStatusDTO(MeasurementStatus status, Integer measurementDurationSec,
                                          MeasurementFailureReason failureReason, String failureDetail) {
            this(status, measurementDurationSec, failureReason, failureDetail, null);
        }

        public UpdateMeasurementStatusDTO(MeasurementStatus status, Integer measurementDurationSec) {
            this.status = status;
            this.measurementDurationSec = measurementDurationSec;
        }
    }
}
