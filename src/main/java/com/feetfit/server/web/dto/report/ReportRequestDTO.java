package com.feetfit.server.web.dto.report;

import com.feetfit.server.domain.enums.GaugeStatus;
import com.feetfit.server.domain.enums.MetricType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
    }

    // ─── 파트별 저장 DTOs ──────────────────────────────────────────────────────

    @Getter
    @NoArgsConstructor
    @Schema(description = "오늘의 발 컨디션 저장 요청 (POST /daily-foot-analysis/condition)")
    public static class ConditionPartDTO {

        @Schema(description = "측정 세션 ID", example = "1")
        @NotNull(message = "측정 세션 ID는 필수입니다.")
        private Long measurementSessionId;

        @Schema(description = "종합 발 상태 레벨", example = "ATTENTION_NEEDED",
                allowableValues = {"VERY_GOOD", "ATTENTION_NEEDED", "NEED_IMPROVEMENT"})
        @NotNull(message = "오늘의 발 컨디션 레벨은 필수입니다.")
        private GaugeStatus conditionLevel;

        @Schema(description = "발 컨디션 코멘트 목록", example = "[\"오른발에 압력이 조금 더 실려 있어요.\", \"발 냄새 위험도는 낮은 편이에요.\"]")
        @NotNull(message = "오늘의 발 컨디션 코멘트는 필수입니다.")
        private List<String> conditionComments;
    }

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
    @Schema(description = "발 눌림 영역 세그먼테이션 이미지 저장 요청 (POST /daily-foot-analysis/plantar-footprint)")
    public static class PlantarFootprintImageDTO {

        @Schema(description = "측정 세션 ID", example = "1")
        @NotNull(message = "측정 세션 ID는 필수입니다.")
        private Long measurementSessionId;
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
                      "measurementSessionId": 1
                    }
                    """
        )
        private String request;

        @Schema(description = "발 눌림 영역 세그먼테이션 결과 이미지 파일", type = "string", format = "binary")
        private String plantarFootprintImage;
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

        @Schema(description = "평균 온도(°C)", example = "34.0")
        @NotNull(message = "평균 온도는 필수입니다.")
        private Float avgTemperatureCelsius;

        @Schema(description = "평균 습도(%)", example = "50.0")
        @NotNull(message = "평균 습도는 필수입니다.")
        private Float avgHumidityPercent;
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "관리팁 저장 요청 (POST /daily-foot-analysis/care-tips)")
    public static class CareTipsPartDTO {

        @Schema(description = "측정 세션 ID", example = "1")
        @NotNull(message = "측정 세션 ID는 필수입니다.")
        private Long measurementSessionId;

        @Schema(description = "관리 팁 목록 (정확히 3개)",
                example = "[\"오른발 앞꿈치 스트레칭을 해주세요.\", \"신발은 착용 후 충분히 말려주세요.\", \"발볼이 좁은 신발은 피하는 것이 좋아요.\"]")
        @NotNull(message = "관리 팁은 필수입니다.")
        @Size(min = 3, max = 3, message = "관리 팁은 3개여야 합니다.")
        private List<String> careTips;

        @Schema(description = "발 타입 텍스트", example = "발의 아치가 낮아 발바닥이 넓게 닿는 편이에요. 오래 걷거나 서 있으면 피로가 커질 수 있어 아치를 잘 받쳐주는 신발이 더 편안할 수 있어요.")
        @NotBlank(message = "발 타입 텍스트는 필수입니다.")
        private String typeText;
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
