package com.feetfit.server.web.dto.report;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.feetfit.server.domain.enums.GaugeStatus;
import com.feetfit.server.domain.enums.MetricType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class ReportRequestDTO {

    @Getter
    @NoArgsConstructor
    @Schema(description = "무지외반 분석 결과 저장 요청")
    public static class SaveHalluxValgusDTO {

        @Schema(description = "측정 세션 ID", example = "1")
        @NotNull(message = "측정 세션 ID는 필수입니다.")
        private Long measurementSessionId;

        @Schema(description = "왼발 엄지발가락 각도", example = "23.5")
        @NotNull(message = "왼발 엄지발가락 각도는 필수입니다.")
        private Float leftToeAngleDegree;

        @Schema(description = "오른발 엄지발가락 각도", example = "15.2")
        @NotNull(message = "오른발 엄지발가락 각도는 필수입니다.")
        private Float rightToeAngleDegree;

        @Schema(description = "종합 점수 분석 설명", example = "왼발 중심으로 무지외반 진행 가능성이 있어 관리가 필요합니다.")
        @NotBlank(message = "종합 점수 분석 설명은 필수입니다.")
        private String scoreAnalysisText;
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "무지외반 분석 결과 저장 multipart 요청")
    public static class SaveHalluxValgusMultipartDTO {

        @Schema(
                description = "무지외반 분석 JSON 문자열 파트",
                type = "string",
                example = """
                    {
                      "measurementSessionId": 1,
                      "leftToeAngleDegree": 23.5,
                      "rightToeAngleDegree": 15.2,
                      "scoreAnalysisText": "왼발 중심으로 무지외반 진행 가능성이 있어 관리가 필요합니다."
                    }
                    """
        )
        private String request;

        @Schema(description = "왼발 키포인트+선분 추출 이미지 파일", type = "string", format = "binary")
        private String leftFootImage;

        @Schema(description = "오른발 키포인트+선분 추출 이미지 파일", type = "string", format = "binary")
        private String rightFootImage;
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "무좀 분석 결과 저장 요청")
    public static class SaveTinaPedisAnalysisDTO {

        @Schema(description = "측정 세션 ID", example = "1")
        @NotNull(message = "측정 세션 ID는 필수입니다.")
        private Long measurementSessionId;

        @Schema(description = "진균 의심 안전 점수. 높을수록 안전합니다.", example = "82", minimum = "0", maximum = "100")
        @NotNull(message = "진균 의심 안전 점수는 필수입니다.")
        @Min(value = 0, message = "진균 의심 안전 점수는 0 이상이어야 합니다.")
        @Max(value = 100, message = "진균 의심 안전 점수는 100 이하이어야 합니다.")
        private Integer fungalSuspicionSafetyScore;

        @Schema(description = "피부 반응 안전 점수. 높을수록 안전합니다.", example = "76", minimum = "0", maximum = "100")
        @NotNull(message = "피부 반응 안전 점수는 필수입니다.")
        @Min(value = 0, message = "피부 반응 안전 점수는 0 이상이어야 합니다.")
        @Max(value = 100, message = "피부 반응 안전 점수는 100 이하이어야 합니다.")
        private Integer skinReactionSafetyScore;

        @Schema(description = "진균 의심 분석 설명", example = "발가락 사이 일부 영역에서 진균 의심도가 낮게 관찰됩니다.")
        @NotBlank(message = "진균 의심 안전 설명은 필수입니다.")
        private String fungalSuspicionSafetyDescription;

        @Schema(description = "피부 반응 분석 설명", example = "피부 발적과 자극 반응은 경미한 수준입니다.")
        @NotBlank(message = "피부 반응 안전 설명은 필수입니다.")
        private String skinReactionSafetyDescription;

        @Schema(description = "종합 점수 설명", example = "전반적으로 안전하지만 발 건조 관리가 필요합니다.")
        @NotBlank(message = "종합 점수 설명은 필수입니다.")
        private String totalScoreDescription;
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "무좀 분석 결과 저장 multipart 요청")
    public static class SaveTinaPedisAnalysisMultipartDTO {

        @Schema(
                description = "무좀 분석 JSON 문자열 파트",
                type = "string",
                example = """
                        {
                          "measurementSessionId": 1,
                          "fungalSuspicionSafetyScore": 82,
                          "skinReactionSafetyScore": 76,
                          "fungalSuspicionSafetyDescription": "발가락 사이 일부 영역에서 진균 의심도가 낮게 관찰됩니다.",
                          "skinReactionSafetyDescription": "피부 발적과 자극 반응은 경미한 수준입니다.",
                          "totalScoreDescription": "전반적으로 안전하지만 발 건조 관리가 필요합니다."
                        }
                        """
        )
        private String request;

        @Schema(description = "의심 부위 표시 이미지 파일", type = "string", format = "binary")
        private String suspiciousAreaMapImage;

        @Schema(description = "원본 발 이미지 파일", type = "string", format = "binary")
        private String originalFootImage;

        @Schema(description = "발바닥 의심 부위 표시 이미지 파일", type = "string", format = "binary")
        private String soleSuspiciousAreaMapImage;

        @Schema(description = "발바닥 원본 이미지 파일", type = "string", format = "binary")
        private String soleOriginalFootImage;
    }

    // ─── 파트별 저장 DTOs ──────────────────────────────────────────────────────

    @Getter
    @NoArgsConstructor
    @Schema(description = "자세 균형 저장 요청 (POST /daily-foot-analysis/balance)")
    public static class BalancePartDTO {

        @Schema(description = "측정 세션 ID", example = "1")
        @NotNull(message = "측정 세션 ID는 필수입니다.")
        private Long measurementSessionId;

        @Schema(description = "자세 균형 점수", example = "72.0")
        @NotNull(message = "자세 균형 점수는 필수입니다.")
        private Float balanceScore;

        @Schema(description = "자세 균형 코멘트", example = "자세 균형에 대한 내용입니다.")
        @NotBlank(message = "자세 균형 코멘트는 필수입니다.")
        private String balanceComment;
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "압력 분포 저장 요청 (POST /daily-foot-analysis/pressure) — multipart/form-data")
    public static class PressurePartDTO {

        @Schema(description = "측정 세션 ID", example = "1")
        @NotNull(message = "측정 세션 ID는 필수입니다.")
        private Long measurementSessionId;

        @Schema(description = "왼발 압력 비율(%)", example = "46.0")
        private Float leftPressurePercent;

        @Schema(description = "오른발 압력 비율(%)", example = "54.0")
        private Float rightPressurePercent;
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "압력 분포 저장 multipart 요청 (POST /daily-foot-analysis/pressure)")
    public static class PressurePartMultipartDTO {

        @Schema(
                description = "압력 분포 JSON 문자열 파트",
                type = "string",
                example = """
                    {
                      "measurementSessionId": 1,
                      "leftPressurePercent": 46.0,
                      "rightPressurePercent": 54.0
                    }
                    """
        )
        private String request;

        @Schema(description = "왼발 압력 분포 이미지 파일", type = "string", format = "binary")
        private String leftPressureImage;

        @Schema(description = "오른발 압력 분포 이미지 파일", type = "string", format = "binary")
        private String rightPressureImage;
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "좌우 발 압력 히트맵 이미지 저장 요청 (POST /daily-foot-analysis/pressure-heatmap)")
    public static class PressureHeatmapImageDTO {

        @Schema(description = "측정 세션 ID", example = "1")
        @NotNull(message = "측정 세션 ID는 필수입니다.")
        private Long measurementSessionId;

        @Schema(
                description = "왼발 C0~C11 순서의 압력 센서 값 12개",
                example = "[50.5, 0.0, 793.7, 157.6, 26.4, 7908.9, 3204.6, 56.7, 1859.7, 14810.9, 9935.6, 10556.9]"
        )
        @NotNull(message = "leftPressureValues(왼발 압력 센서 값 목록)는 필수입니다.")
        @Size(min = 12, max = 12, message = "leftPressureValues는 C0~C11 순서의 12개 값이어야 합니다.")
        private List<
                @NotNull(message = "왼발 압력 센서 값에는 null이 들어갈 수 없습니다.")
                @DecimalMin(value = "0.0", message = "왼발 압력 센서 값은 0 이상이어야 합니다.")
                Float> leftPressureValues;

        @Schema(
                description = "오른발 C0~C11 순서의 압력 센서 값 12개",
                example = "[48.5, 0.0, 812.3, 160.1, 30.0, 7800.2, 3100.5, 60.0, 1900.0, 14000.1, 9700.4, 10100.8]"
        )
        @NotNull(message = "rightPressureValues(오른발 압력 센서 값 목록)는 필수입니다.")
        @Size(min = 12, max = 12, message = "rightPressureValues는 C0~C11 순서의 12개 값이어야 합니다.")
        private List<
                @NotNull(message = "오른발 압력 센서 값에는 null이 들어갈 수 없습니다.")
                @DecimalMin(value = "0.0", message = "오른발 압력 센서 값은 0 이상이어야 합니다.")
                Float> rightPressureValues;

        @Schema(description = "압력 측정 기록 시각. 생략 시 서버 현재 시각으로 저장", example = "2026-08-24T10:30:00")
        private LocalDateTime recordedAt;
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "좌우 발 압력 히트맵 이미지 저장 multipart 요청 (POST /daily-foot-analysis/pressure-heatmap)")
    public static class PressureHeatmapImageMultipartDTO {

        @Schema(
                description = "좌우 발 압력 히트맵 이미지 저장 JSON 문자열 파트",
                type = "string",
                example = """
                    {
                      "measurementSessionId": 1,
                      "leftPressureValues": [50.5, 0.0, 793.7, 157.6, 26.4, 7908.9, 3204.6, 56.7, 1859.7, 14810.9, 9935.6, 10556.9],
                      "rightPressureValues": [48.5, 0.0, 812.3, 160.1, 30.0, 7800.2, 3100.5, 60.0, 1900.0, 14000.1, 9700.4, 10100.8]
                    }
                    """
        )
        private String request;

        @Schema(description = "왼발 압력 히트맵 이미지 파일", type = "string", format = "binary")
        private String leftPressureHeatmapImage;

        @Schema(description = "오른발 압력 히트맵 이미지 파일", type = "string", format = "binary")
        private String rightPressureHeatmapImage;
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "발 눌림 영역 세그먼테이션 이미지 저장 요청 (POST /daily-foot-analysis/plantar-footprint)")
    public static class PlantarFootprintImageDTO {

        @Schema(description = "측정 세션 ID", example = "1")
        @NotNull(message = "측정 세션 ID는 필수입니다.")
        private Long measurementSessionId;

        @Schema(description = "발 눌림 분석 결과 텍스트", example = "왼발 뒤꿈치와 오른발 앞꿈치에 압력이 집중되어 있습니다.")
        @NotBlank(message = "발 눌림 분석 결과 텍스트는 필수입니다.")
        private String plantarFootprintAnalysisText;
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "발 눌림 영역 세그먼테이션 이미지 저장 multipart 요청 (POST /daily-foot-analysis/plantar-footprint)")
    public static class PlantarFootprintImageMultipartDTO {

        @Schema(
                description = "발 눌림 영역 세그먼테이션 이미지 저장 JSON 문자열 파트",
                type = "string",
                example = """
                    {
                      "measurementSessionId": 1,
                      "plantarFootprintAnalysisText": "왼발 뒤꿈치와 오른발 앞꿈치에 압력이 집중되어 있습니다."
                    }
                    """
        )
        private String request;

        @Schema(description = "왼발 발 눌림 영역 세그먼테이션 결과 이미지 파일", type = "string", format = "binary")
        private String leftPlantarFootprintImage;

        @Schema(description = "오른발 발 눌림 영역 세그먼테이션 결과 이미지 파일", type = "string", format = "binary")
        private String rightPlantarFootprintImage;
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "발 수치 저장 요청 (POST /daily-foot-analysis/metrics)")
    public static class MetricsPartDTO {

        @Schema(description = "측정 세션 ID", example = "1")
        @NotNull(message = "측정 세션 ID는 필수입니다.")
        private Long measurementSessionId;

        @Schema(description = "측정 왼발 길이(mm)", example = "253.0")
        private Float measuredLeftFootSizeMm;

        @Schema(description = "측정 오른발 길이(mm)", example = "248.0")
        private Float measuredRightFootSizeMm;

        @Schema(description = "왼발 볼 너비(mm)", example = "85.0")
        private Float leftFootWidthMm;

        @Schema(description = "오른발 볼 너비(mm)", example = "70.0")
        private Float rightFootWidthMm;
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "환경 상태 저장 요청 (POST /daily-foot-analysis/environment)")
    public static class EnvironmentPartDTO {

        @Schema(description = "측정 세션 ID", example = "1")
        @NotNull(message = "측정 세션 ID는 필수입니다.")
        private Long measurementSessionId;

        @Schema(description = "발을 넣기 전 온도(°C)", example = "28.0")
        private Float beforeTemperatureCelsius;

        @Schema(description = "발을 넣기 전 습도(%)", example = "45.0")
        private Float beforeHumidityPercent;

        @Schema(description = "발을 넣은 후 온도(°C)", example = "34.0")
        private Float afterTemperatureCelsius;

        @Schema(description = "발을 넣은 후 습도(%)", example = "55.0")
        private Float afterHumidityPercent;

        @Schema(description = "평균 온도(°C). 이전/이후 온도가 있으면 서버에서 계산합니다.", example = "31.0")
        private Float avgTemperatureCelsius;

        @Schema(description = "평균 습도(%). 이전/이후 습도가 있으면 서버에서 계산합니다.", example = "50.0")
        private Float avgHumidityPercent;

        @JsonIgnore
        @Schema(hidden = true)
        @AssertTrue(message = "이전/이후 온습도 값 또는 평균 온습도 값은 필수입니다.")
        public boolean isRequiredEnvironmentValuesPresent() {
            return hasBeforeAfterEnvironmentValues() || hasAverageEnvironmentValues();
        }

        @JsonIgnore
        @Schema(hidden = true)
        @AssertTrue(message = "이전/이후 온습도 값은 모두 함께 전달해야 합니다.")
        public boolean isBeforeAfterEnvironmentValuesComplete() {
            return !hasAnyBeforeAfterEnvironmentValue() || hasBeforeAfterEnvironmentValues();
        }

        @JsonIgnore
        @Schema(hidden = true)
        @AssertTrue(message = "평균 온습도 값은 모두 함께 전달해야 합니다.")
        public boolean isAverageEnvironmentValuesComplete() {
            return !hasAnyAverageEnvironmentValue() || hasAverageEnvironmentValues();
        }

        private boolean hasBeforeAfterEnvironmentValues() {
            return beforeTemperatureCelsius != null
                    && beforeHumidityPercent != null
                    && afterTemperatureCelsius != null
                    && afterHumidityPercent != null;
        }

        private boolean hasAverageEnvironmentValues() {
            return avgTemperatureCelsius != null && avgHumidityPercent != null;
        }

        private boolean hasAnyBeforeAfterEnvironmentValue() {
            return beforeTemperatureCelsius != null
                    || beforeHumidityPercent != null
                    || afterTemperatureCelsius != null
                    || afterHumidityPercent != null;
        }

        private boolean hasAnyAverageEnvironmentValue() {
            return avgTemperatureCelsius != null || avgHumidityPercent != null;
        }
    }

    // ─── 기타 DTO ─────────────────────────────────────────────────────────────

    @Getter
    @NoArgsConstructor
    public static class SaveMetricResultDTO {

        @NotNull(message = "측정 세션 ID는 필수입니다.")
        private Long measurementSessionId;

        @NotNull(message = "지표 타입은 필수입니다.")
        private MetricType metricType;

        @NotNull(message = "점수는 필수입니다.")
        private Float score;

        @NotNull(message = "어드바이스는 필수입니다.")
        @Size(min = 2, max = 2, message = "어드바이스는 2개여야 합니다.")
        private List<String> advice;
    }

    @Getter
    @NoArgsConstructor
    public static class SaveMetricAnalysisResultDTO {

        @NotNull(message = "지표 타입은 필수입니다.")
        private MetricType metricType;

        @NotNull(message = "점수는 필수입니다.")
        private Float score;

        @NotNull(message = "상태는 필수입니다.")
        private GaugeStatus status;

        @NotNull(message = "어드바이스는 필수입니다.")
        @Size(min = 2, max = 2, message = "어드바이스는 2개여야 합니다.")
        private List<String> advice;
    }
}
