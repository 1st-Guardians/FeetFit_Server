package com.feetfit.server.web.dto.report;

import com.feetfit.server.domain.enums.GaugeStatus;
import com.feetfit.server.domain.enums.MetricType;
import com.feetfit.server.domain.enums.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

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

        @Schema(description = "측정 세션 ID", example = "1")
        private Long measurementSessionId;

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

        @Schema(description = "측정 세션 ID", example = "1")
        private Long measurementSessionId;

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

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "종합 발 분석 결과 응답")
    public static class DailyFootAnalysisResultDTO {

        @Schema(description = "종합 발 분석 결과 ID", example = "1")
        private Long id;

        @Schema(description = "측정 세션 ID", example = "1")
        private Long measurementSessionId;

        // 오늘의 발 컨디션
        @Schema(description = "종합 상태", example = "ATTENTION_NEEDED",
                allowableValues = {"VERY_GOOD", "ATTENTION_NEEDED", "NEED_IMPROVEMENT"})
        private GaugeStatus conditionLevel;

        @Schema(description = "컨디션 코멘트 목록",
                example = "[\"오른발에 압력이 조금 더 실려 있어요.\", \"발 냄새 위험도는 낮은 편이에요.\"]")
        private List<String> conditionComments;

        // 자세 균형
        @Schema(description = "자세 균형 점수", example = "72.0")
        private Float balanceScore;

        @Schema(description = "자세 균형 코멘트", example = "자세 균형에 대한 내용을 적을겁니다.")
        private String balanceComment;

        @Schema(description = "이전 측정 대비 균형 점수 변화. 양수면 증가, 음수면 감소. 이전 데이터 없으면 null",
                example = "4.0", nullable = true)
        private Float balanceScoreDiff;

        // 압력 분포
        @Schema(description = "왼발 압력 비율 (%)", example = "46.0")
        private Float leftPressurePercent;

        @Schema(description = "오른발 압력 비율 (%)", example = "54.0")
        private Float rightPressurePercent;

        @Schema(description = "왼발 압력 분포 이미지 URL", example = "https://example.com/left-pressure.jpg")
        private String leftPressureImageUrl;

        @Schema(description = "오른발 압력 분포 이미지 URL", example = "https://example.com/right-pressure.jpg")
        private String rightPressureImageUrl;

        // 발 수치
        @Schema(description = "사용자가 최초 입력한 발 사이즈 (mm)", example = "250")
        private Integer userFootSize;

        @Schema(description = "측정된 왼발 길이 (mm)", example = "253.0")
        private Float measuredLeftFootSizeMm;

        @Schema(description = "측정된 오른발 길이 (mm)", example = "248.0")
        private Float measuredRightFootSizeMm;

        @Schema(description = "입력 사이즈 대비 왼발 변화 (mm). 양수면 증가, 음수면 감소. null이면 측정값 없음",
                example = "3.0", nullable = true)
        private Float leftFootSizeDiff;

        @Schema(description = "입력 사이즈 대비 오른발 변화 (mm). 양수면 증가, 음수면 감소. null이면 측정값 없음",
                example = "-2.0", nullable = true)
        private Float rightFootSizeDiff;

        @Schema(description = "왼발 너비 (mm)", example = "85.0")
        private Float leftFootWidthMm;

        @Schema(description = "오른발 너비 (mm)", example = "70.0")
        private Float rightFootWidthMm;

        // 발 냄새
        @Schema(description = "발 냄새 수치 (ppm)", example = "76.0")
        private Float footOdourPpm;

        @Schema(description = "발 냄새 코멘트", example = "발 냄새 위험도는 76ppm으로 낮은 편이에요.")
        private String footOdourComment;

        // 발 환경 상태
        @Schema(description = "평균 온도 (°C)", example = "34.0")
        private Float avgTemperatureCelsius;

        @Schema(description = "평균 습도 (%)", example = "50.0")
        private Float avgHumidityPercent;

        // 오늘의 관리 팁
        @Schema(description = "관리 팁 목록 (3가지)",
                example = "[\"오른발 앞꿈치 스트레칭을 해주세요.\", \"신발은 착용 후 충분히 말려주세요.\", \"발볼이 좁은 신발은 피하는 것이 좋아요.\"]")
        private List<String> careTips;

        // 신발 리스트 페이지용
        @Schema(description = "발 타입 텍스트 (신발 리스트 페이지 - 00님의 발 타입은요...)",
                example = "발의 아치가 낮아 발바닥이 넓게 닿는 편이에요. 오래 걷거나 서 있으면 피로가 커질 수 있어 아치를 잘 받쳐주는 신발이 더 편안할 수 있어요.")
        private String typeText;

        @Schema(description = "생성 시각", example = "2026-05-20T09:00:00")
        private LocalDateTime createdAt;

        @Schema(description = "수정 시각", example = "2026-05-20T09:00:00")
        private LocalDateTime updatedAt;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "발 타입 텍스트 조회 응답")
    public static class FootTypeTextResultDTO {

        @Schema(description = "유저 닉네임", example = "민지")
        private String nickname;

        @Schema(description = "발 타입 텍스트. 측정 이력 없으면 null",
                example = "발의 아치가 낮아 발바닥이 넓게 닿는 편이에요.",
                nullable = true)
        private String typeText;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "요약 페이지 조회 응답")
    public static class ReportSummaryResultDTO {

        @Schema(description = "발 종합 점수", example = "83")
        private Integer totalScore;

        @Schema(description = "지표별 분석 결과 목록")
        private List<MetricScoreDTO> metricScores;

        @Schema(description = "1년간 월별 종합 점수 평균 변화 추이")
        private List<MonthlyScoreDTO> monthlyScores;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "지표별 분석 결과")
    public static class MetricScoreDTO {

        @Schema(description = "지표 타입", example = "PRESSURE_BALANCE",
                allowableValues = {"PRESSURE_BALANCE", "HALLUX_VALGUS", "ATHLETES_FOOT", "FOOT_ODOR", "FOOT_ENVIRONMENT"})
        private MetricType metricType;

        @Schema(description = "점수", example = "85.0")
        private Float score;

        @Schema(description = "지표 상태", example = "VERY_GOOD",
                allowableValues = {"VERY_GOOD", "ATTENTION_NEEDED", "NEED_IMPROVEMENT"})
        private GaugeStatus status;

        @Schema(description = "어드바이스 목록 (2개)",
                example = "[\"좌우 발의 압력 분포에 다소 차이가 나타나고 있습니다.\", \"보행 시 체중을 양쪽 발에 고르게 분산하는 습관을 의식하는 것이 필요합니다.\"]")
        private List<String> advice;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "월별 점수")
    public static class MonthlyScoreDTO {

        @Schema(description = "월 (1~12)", example = "1")
        private Integer month;

        @Schema(description = "해당 월 종합 점수 평균", example = "78.5")
        private Float avgScore;
    }
}
