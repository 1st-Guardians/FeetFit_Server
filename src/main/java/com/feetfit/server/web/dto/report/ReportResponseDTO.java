package com.feetfit.server.web.dto.report;

import com.feetfit.server.domain.enums.RiskLevel;
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
    public static class SaveHalluxValgusResultDTO {
        private Long id;
        private String imageUrl;

        // 왼발
        private Float leftToeAngleDegree;
        private RiskLevel leftRiskLevel;
        private String leftAnalysisText;

        // 오른발
        private Float rightToeAngleDegree;
        private RiskLevel rightRiskLevel;
        private String rightAnalysisText;

        // 종합
        private Float riskScore;
        private String scoreAnalysisText;

        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HalluxValgusResultDTO {
        private Long id;
        private String imageUrl;

        // 왼발
        private Float leftToeAngleDegree;
        private RiskLevel leftRiskLevel;
        private String leftAnalysisText;

        // 오른발
        private Float rightToeAngleDegree;
        private RiskLevel rightRiskLevel;
        private String rightAnalysisText;

        // 종합
        private Float riskScore;
        private String scoreAnalysisText;

        // 이전 측정 대비 점수 변화 (이전 데이터 없으면 null)
        private Float previousRiskScore;
        private Float riskScoreDiff;        // 양수면 +, 음수면 -

        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
