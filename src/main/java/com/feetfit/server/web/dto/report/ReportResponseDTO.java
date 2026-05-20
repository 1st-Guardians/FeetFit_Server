package com.feetfit.server.web.dto.report;

import com.feetfit.server.domain.enums.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
public class ReportResponseDTO {

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "무지외반 분석 결과 저장 응답")
    public static class SaveHalluxValgusResultDTO {
        @Schema(description = "무지외반 분석 결과 ID", example = "1")
        private Long id;

        @Schema(description = "분석 이미지 URL", example = "https://example.com/hallux/foot-001.jpg")
        private String imageUrl;

        // 왼발
        @Schema(description = "왼발 엄지발가락 각도", example = "23.5")
        private Float leftToeAngleDegree;

        @Schema(description = "왼발 위험도", example = "MEDIUM", allowableValues = {"LOW", "MEDIUM", "HIGH"})
        private RiskLevel leftRiskLevel;

        @Schema(description = "왼발 분석 설명", example = "왼발 무지외반 주의가 필요합니다.")
        private String leftAnalysisText;

        // 오른발
        @Schema(description = "오른발 엄지발가락 각도", example = "15.2")
        private Float rightToeAngleDegree;

        @Schema(description = "오른발 위험도", example = "LOW", allowableValues = {"LOW", "MEDIUM", "HIGH"})
        private RiskLevel rightRiskLevel;

        @Schema(description = "오른발 분석 설명", example = "오른발은 현재 정상 범위에 가깝습니다.")
        private String rightAnalysisText;

        // 종합
        @Schema(description = "종합 위험도 점수", example = "75.5")
        private Float riskScore;

        @Schema(description = "종합 점수 분석 설명", example = "왼발 중심으로 관리가 필요합니다.")
        private String scoreAnalysisText;

        @Schema(description = "생성 시각", example = "2026-05-20T09:00:00")
        private LocalDateTime createdAt;

        @Schema(description = "수정 시각", example = "2026-05-20T09:00:00")
        private LocalDateTime updatedAt;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "무지외반 분석 결과 조회 응답")
    public static class HalluxValgusResultDTO {
        @Schema(description = "무지외반 분석 결과 ID", example = "1")
        private Long id;

        @Schema(description = "분석 이미지 URL", example = "https://example.com/hallux/foot-001.jpg")
        private String imageUrl;

        // 왼발
        @Schema(description = "왼발 엄지발가락 각도", example = "23.5")
        private Float leftToeAngleDegree;

        @Schema(description = "왼발 위험도", example = "MEDIUM", allowableValues = {"LOW", "MEDIUM", "HIGH"})
        private RiskLevel leftRiskLevel;

        @Schema(description = "왼발 분석 설명", example = "왼발 무지외반 주의가 필요합니다.")
        private String leftAnalysisText;

        // 오른발
        @Schema(description = "오른발 엄지발가락 각도", example = "15.2")
        private Float rightToeAngleDegree;

        @Schema(description = "오른발 위험도", example = "LOW", allowableValues = {"LOW", "MEDIUM", "HIGH"})
        private RiskLevel rightRiskLevel;

        @Schema(description = "오른발 분석 설명", example = "오른발은 현재 정상 범위에 가깝습니다.")
        private String rightAnalysisText;

        // 종합
        @Schema(description = "종합 위험도 점수", example = "75.5")
        private Float riskScore;

        @Schema(description = "종합 점수 분석 설명", example = "왼발 중심으로 관리가 필요합니다.")
        private String scoreAnalysisText;

        // 이전 측정 대비 점수 변화 (이전 데이터 없으면 null)
        @Schema(description = "이전 측정 종합 위험도 점수. 이전 데이터가 없으면 null", example = "70.0", nullable = true)
        private Float previousRiskScore;

        @Schema(description = "이전 측정 대비 점수 변화. 양수면 증가, 음수면 감소", example = "5.5", nullable = true)
        private Float riskScoreDiff;        // 양수면 +, 음수면 -

        @Schema(description = "생성 시각", example = "2026-05-20T09:00:00")
        private LocalDateTime createdAt;

        @Schema(description = "수정 시각", example = "2026-05-20T09:00:00")
        private LocalDateTime updatedAt;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "무좀 분석 결과 응답")
    public static class TinaPedisAnalysisResultDTO {
        @Schema(description = "무좀 분석 결과 ID", example = "1")
        private Long id;

        @Schema(description = "측정 세션 ID", example = "1")
        private Long measurementSessionId;

        @Schema(description = "진균 의심 안전 점수. 높을수록 안전합니다.", example = "82")
        private Integer fungalSuspicionSafetyScore;

        @Schema(description = "피부 반응 안전 점수. 높을수록 안전합니다.", example = "76")
        private Integer skinReactionSafetyScore;

        @Schema(description = "종합 안전 점수. 진균 의심 안전 점수 70%, 피부 반응 안전 점수 30%로 계산합니다.", example = "80.2")
        private Float totalScore;

        @Schema(description = "바로 이전 측정의 종합 안전 점수. 이전 데이터가 없으면 null", example = "74.5", nullable = true)
        private Float previousTotalScore;

        @Schema(description = "현재 종합 안전 점수와 바로 이전 종합 안전 점수의 차이. 이전 데이터가 없으면 null", example = "5.7", nullable = true)
        private Float totalScoreDiff;

        @Schema(description = "진균 의심 분석 설명", example = "발가락 사이 일부 영역에서 진균 의심도가 낮게 관찰됩니다.")
        private String fungalSuspicionSafetyDescription;

        @Schema(description = "피부 반응 분석 설명", example = "피부 발적과 자극 반응은 경미한 수준입니다.")
        private String skinReactionSafetyDescription;

        @Schema(description = "종합 점수 설명", example = "전반적으로 안전하지만 발 건조 관리가 필요합니다.")
        private String totalScoreDescription;

        @Schema(description = "의심 부위 표시 이미지 URL", example = "https://example.com/tina-pedis/map.png")
        private String suspiciousAreaMapImageUrl;

        @Schema(description = "원본 발 이미지 URL", example = "https://example.com/tina-pedis/original.png")
        private String originalFootImageUrl;

        @Schema(description = "분석 기록 시각", example = "2026-05-20T09:00:00")
        private LocalDateTime recordedAt;

        @Schema(description = "생성 시각", example = "2026-05-20T09:00:00")
        private LocalDateTime createdAt;

        @Schema(description = "수정 시각", example = "2026-05-20T09:00:00")
        private LocalDateTime updatedAt;
    }
}
