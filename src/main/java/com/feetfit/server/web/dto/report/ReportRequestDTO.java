package com.feetfit.server.web.dto.report;

import com.feetfit.server.domain.enums.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

        @Schema(description = "의심 부위 표시 이미지 URL", example = "https://example.com/tina-pedis/map.png")
        @NotBlank(message = "의심 부위 맵 이미지 URL은 필수입니다.")
        private String suspiciousAreaMapImageUrl;

        @Schema(description = "원본 발 이미지 URL", example = "https://example.com/tina-pedis/original.png")
        @NotBlank(message = "원본 발 이미지 URL은 필수입니다.")
        private String originalFootImageUrl;

        @Schema(description = "분석 기록 시각", example = "2026-05-20T09:00:00")
        @NotNull(message = "기록 시각은 필수입니다.")
        private LocalDateTime recordedAt;
    }
}
