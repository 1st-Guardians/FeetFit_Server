package com.feetfit.server.web.dto.measurement;

import com.feetfit.server.domain.enums.MeasurementStatus;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "측정 세션 생성 응답")
    public static class CreateMeasurementSessionResultDTO {
        @Schema(description = "측정 세션 ID", example = "1")
        private Long id;

        @Schema(description = "측정에 사용된 디바이스 ID", example = "1")
        private Long deviceId;

        @Schema(description = "측정 상태", example = "MEASURING", allowableValues = {"PENDING", "MEASURING", "TRANSFERRING", "COMPLETED", "FAILED"})
        private MeasurementStatus status;

        @Schema(description = "측정 시작 시각", example = "2026-05-20T09:00:00")
        private LocalDateTime measuredAt;

        @Schema(description = "생성 시각", example = "2026-05-20T09:00:00")
        private LocalDateTime createdAt;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "측정 세션 상태 수정 응답")
    public static class UpdateMeasurementStatusResultDTO {
        @Schema(description = "측정 세션 ID", example = "1")
        private Long id;

        @Schema(description = "측정 상태", example = "COMPLETED", allowableValues = {"PENDING", "MEASURING", "TRANSFERRING", "COMPLETED", "FAILED"})
        private MeasurementStatus status;

        @Schema(description = "측정 소요 시간(초)", example = "180")
        private Integer measurementDurationSec;

        @Schema(description = "수정 시각", example = "2026-05-20T09:03:00")
        private LocalDateTime updatedAt;
    }
}
