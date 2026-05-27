package com.feetfit.server.web.dto.report;

import com.feetfit.server.domain.enums.GaugeStatus;
import com.feetfit.server.domain.enums.MetricType;
import com.feetfit.server.domain.enums.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
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

        @Schema(description = "분석 이미지 URL", example = "https://example.com/hallux/foot-001.jpg")
        private String imageUrl;

        // 왼발
        @Schema(description = "왼발 엄지발가락 각도", example = "23.5")
        private Float leftToeAngleDegree;

        @Schema(description = "왼발 위험도", example = "MEDIUM", allowableValues = {"LOW", "MEDIUM", "HIGH"})
        @NotNull(message = "왼발 위험도 레벨은 필수입니다.")
        private RiskLevel leftRiskLevel;

        @Schema(description = "왼발 분석 설명", example = "왼발 엄지발가락 각도가 정상 범위보다 커 주기적인 스트레칭이 필요합니다.")
        private String leftAnalysisText;

        // 오른발
        @Schema(description = "오른발 엄지발가락 각도", example = "15.2")
        private Float rightToeAngleDegree;

        @Schema(description = "오른발 위험도", example = "LOW", allowableValues = {"LOW", "MEDIUM", "HIGH"})
        @NotNull(message = "오른발 위험도 레벨은 필수입니다.")
        private RiskLevel rightRiskLevel;

        @Schema(description = "오른발 분석 설명", example = "오른발은 현재 정상 범위에 가깝습니다.")
        private String rightAnalysisText;

        // 종합
        @Schema(description = "종합 위험도 점수", example = "75.5")
        @NotNull(message = "종합 위험도 점수는 필수입니다.")
        private Float riskScore;

        @Schema(description = "종합 점수 분석 설명", example = "왼발 중심으로 무지외반 진행 가능성이 있어 관리가 필요합니다.")
        private String scoreAnalysisText;
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

    @Getter
    @NoArgsConstructor
    public static class SaveDailyFootAnalysisDTO {

        @NotNull(message = "측정 세션 ID는 필수입니다.")
        private Long measurementSessionId;

        @NotNull(message = "종합 상태는 필수입니다.")
        private GaugeStatus conditionLevel;

        @NotNull(message = "상태 코멘트는 필수입니다.")
        private List<String> conditionComments;

        @NotNull(message = "균형 점수는 필수입니다.")
        private Float balanceScore;

        @NotBlank(message = "균형 코멘트는 필수입니다.")
        private String balanceComment;

        private Float leftPressurePercent;
        private Float rightPressurePercent;
        private String leftPressureImageUrl;
        private String rightPressureImageUrl;
        private Float measuredLeftFootSizeMm;
        private Float measuredRightFootSizeMm;
        private Float leftFootWidthMm;
        private Float rightFootWidthMm;
        private Float footOdourPpm;
        private String footOdourComment;

        @NotNull(message = "평균 온도는 필수입니다.")
        private Float avgTemperatureCelsius;

        @NotNull(message = "평균 습도는 필수입니다.")
        private Float avgHumidityPercent;

        @NotNull(message = "관리 팁은 필수입니다.")
        @Size(min = 3, max = 3, message = "관리 팁은 3개여야 합니다.")
        private List<String> careTips;

        @NotBlank(message = "발 타입 텍스트는 필수입니다.")
        private String typeText;
    }

    @Getter
    @NoArgsConstructor
    public static class SaveReportDTO {

        @NotNull(message = "측정 세션 ID는 필수입니다.")
        private Long measurementSessionId;

        @NotNull(message = "지표별 분석 결과는 필수입니다.")
        @Size(min = 5, max = 5, message = "지표별 분석 결과는 5개여야 합니다.")
        @Valid
        private List<SaveMetricAnalysisResultDTO> metricAnalysisResults;
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
