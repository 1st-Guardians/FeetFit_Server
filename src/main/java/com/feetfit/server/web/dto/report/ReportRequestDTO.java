package com.feetfit.server.web.dto.report;

import com.feetfit.server.domain.enums.RiskLevel;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
public class ReportRequestDTO {

    @Getter
    @NoArgsConstructor
    public static class SaveHalluxValgusDTO {

        @NotNull(message = "측정 세션 ID는 필수입니다.")
        private Long measurementSessionId;

        private String imageUrl;

        // 왼발
        private Float leftToeAngleDegree;

        @NotNull(message = "왼발 위험도 레벨은 필수입니다.")
        private RiskLevel leftRiskLevel;

        private String leftAnalysisText;

        // 오른발
        private Float rightToeAngleDegree;

        @NotNull(message = "오른발 위험도 레벨은 필수입니다.")
        private RiskLevel rightRiskLevel;

        private String rightAnalysisText;

        // 종합
        @NotNull(message = "종합 위험도 점수는 필수입니다.")
        private Float riskScore;

        private String scoreAnalysisText;
    }
}