package com.feetfit.server.web.dto.measurement;

import com.feetfit.server.domain.enums.MeasurementStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

        @Schema(description = "측정 상태", example = "WAITING_FOR_PHOTO", allowableValues = {"WAITING_FOR_PHOTO", "READY_FOR_PHOTO", "CAPTURING_PHOTO", "WAITING_FOR_PRESSURE", "READY_FOR_PRESSURE", "MEASURING_PRESSURE", "PROCESSING", "COMPLETED", "FAILED", "PENDING", "MEASURING", "TRANSFERRING"})
        private MeasurementStatus status;

        @Schema(description = "측정 시작 시각", example = "2026-05-20T09:00:00")
        private LocalDateTime measuredAt;

        @Schema(description = "생성 시각", example = "2026-05-20T09:00:00")
        private LocalDateTime createdAt;

        @Schema(description = "측정 상태 WebSocket 구독 topic", example = "/topic/measurements/1")
        private String webSocketTopic;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "측정 세션 상태 수정 응답")
    public static class UpdateMeasurementStatusResultDTO {
        @Schema(description = "측정 세션 ID", example = "1")
        private Long id;

        @Schema(description = "측정 상태", example = "COMPLETED", allowableValues = {"WAITING_FOR_PHOTO", "READY_FOR_PHOTO", "CAPTURING_PHOTO", "WAITING_FOR_PRESSURE", "READY_FOR_PRESSURE", "MEASURING_PRESSURE", "PROCESSING", "COMPLETED", "FAILED", "PENDING", "MEASURING", "TRANSFERRING"})
        private MeasurementStatus status;

        @Schema(description = "측정 소요 시간(초)", example = "180")
        private Integer measurementDurationSec;

        @Schema(description = "수정 시각", example = "2026-05-20T09:03:00")
        private LocalDateTime updatedAt;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "오늘 측정 여부 조회 응답")
    public static class TodayMeasurementStatusResultDTO {
        @Schema(description = "오늘 날짜", example = "2026-05-20")
        private LocalDate today;

        @Schema(description = "오늘 완료된 측정 기록 존재 여부", example = "true")
        private Boolean hasTodayMeasurement;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "주간 측정 여부 조회 응답")
    public static class WeeklyMeasurementStatusResultDTO {
        @Schema(description = "오늘 날짜", example = "2026-05-20")
        private LocalDate today;

        @Schema(description = "이번 주 시작일. 일요일 기준", example = "2026-05-17")
        private LocalDate weekStartDate;

        @Schema(description = "이번 주 종료일. 토요일 기준", example = "2026-05-23")
        private LocalDate weekEndDate;

        @Schema(description = "이번 주에 완료된 측정 기록 존재 여부", example = "true")
        private Boolean hasWeeklyMeasurement;

        @Schema(description = "일요일부터 토요일까지 날짜별 측정 여부")
        private List<DailyMeasurementStatusDTO> dailyStatuses;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "날짜별 측정 여부")
    public static class DailyMeasurementStatusDTO {
        @Schema(description = "날짜", example = "2026-05-20")
        private LocalDate date;

        @Schema(description = "요일", example = "WEDNESDAY")
        private String dayOfWeek;

        @Schema(description = "한글 요일", example = "수")
        private String dayOfWeekKor;

        @Schema(description = "해당 날짜에 완료된 측정 기록 존재 여부", example = "true")
        private Boolean hasMeasurement;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "측정 WebSocket 상태 메시지")
    public static class MeasurementSocketMessageDTO {
        @Schema(description = "이벤트 타입", example = "MEASUREMENT_STATUS_CHANGED")
        private String eventType;

        @Schema(description = "측정 세션 ID", example = "1")
        private Long measurementSessionId;

        @Schema(description = "사용자 ID", example = "1")
        private Long userId;

        @Schema(description = "디바이스 ID", example = "1")
        private Long deviceId;

        @Schema(description = "디바이스 고유 코드", example = "FeetFit-001")
        private String deviceName;

        @Schema(description = "측정 상태", example = "WAITING_FOR_PHOTO")
        private MeasurementStatus status;

        @Schema(description = "측정 상태별 사용자 안내 문구", example = "FSR 센서 판을 올리고 유리판 위에 올라와 주세요.")
        private String statusMessage;

        @Schema(description = "메시지 수신 후 WebSocket 연결 종료 권장 여부", example = "false")
        private Boolean shouldDisconnect;

        @Schema(description = "메시지 발행 시각", example = "2026-05-31T22:30:00")
        private LocalDateTime sentAt;
    }
}
