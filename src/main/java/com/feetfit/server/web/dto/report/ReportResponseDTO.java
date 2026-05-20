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
}
