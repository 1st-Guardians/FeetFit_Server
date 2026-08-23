package com.feetfit.server.web.dto.report;

import com.feetfit.server.domain.enums.GaugeStatus;
import com.feetfit.server.domain.enums.HealthType;
import com.feetfit.server.domain.enums.MetricType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
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

        // 왼발
        @Schema(description = "왼발 엄지발가락 각도", example = "23.5")
        private Float leftToeAngleDegree;

        @Schema(description = "왼발 분석 설명")
        private String leftAnalysisText;

        @Schema(description = "왼발 키포인트+선분 추출 이미지 URL")
        private String leftImageUrl;

        // 오른발
        @Schema(description = "오른발 엄지발가락 각도", example = "15.2")
        private Float rightToeAngleDegree;

        @Schema(description = "오른발 분석 설명")
        private String rightAnalysisText;

        @Schema(description = "오른발 키포인트+선분 추출 이미지 URL")
        private String rightImageUrl;

        // 종합
        @Schema(description = "종합 위험도 점수", example = "75.5")
        private Float riskScore;

        @Schema(description = "종합 점수 분석 설명")
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

        // 왼발
        @Schema(description = "왼발 엄지발가락 각도", example = "23.5")
        private Float leftToeAngleDegree;

        @Schema(description = "왼발 분석 설명")
        private String leftAnalysisText;

        @Schema(description = "왼발 키포인트+선분 추출 이미지 URL")
        private String leftImageUrl;

        // 오른발
        @Schema(description = "오른발 엄지발가락 각도", example = "15.2")
        private Float rightToeAngleDegree;

        @Schema(description = "오른발 분석 설명")
        private String rightAnalysisText;

        @Schema(description = "오른발 키포인트+선분 추출 이미지 URL")
        private String rightImageUrl;

        // 종합
        @Schema(description = "종합 위험도 점수", example = "75.5")
        private Float riskScore;

        @Schema(description = "종합 점수 분석 설명")
        private String scoreAnalysisText;

        @Schema(description = "이전 측정 종합 위험도 점수. 이전 데이터가 없으면 null", nullable = true)
        private Float previousRiskScore;

        @Schema(description = "이전 측정 대비 점수 변화. 양수면 증가, 음수면 감소", nullable = true)
        private Float riskScoreDiff;

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

        @Schema(description = "발 눌림 영역 세그먼테이션 결과 이미지 URL", example = "https://project5-42-oregon-feetfit-s3.s3.us-west-2.amazonaws.com/plantar-footprint/7ac068ed-a203-4b95-a280-257a37ce3053.png")
        private String plantarFootprintImageUrl;

        @Schema(description = "왼발 압력 히트맵 이미지 URL", example = "https://project5-42-oregon-feetfit-s3.s3.us-west-2.amazonaws.com/pressure-heatmap-left/7ac068ed-a203-4b95-a280-257a37ce3053.png")
        private String leftPressureHeatmapImageUrl;

        @Schema(description = "오른발 압력 히트맵 이미지 URL", example = "https://project5-42-oregon-feetfit-s3.s3.us-west-2.amazonaws.com/pressure-heatmap-right/e245c3c1-930c-41c1-8423-e60268fe3f17.png")
        private String rightPressureHeatmapImageUrl;

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
    @Schema(description = "발 눌림 영역 세그먼테이션 이미지 저장 응답")
    public static class PlantarFootprintImageResultDTO {

        @Schema(description = "발 눌림 영역 세그먼테이션 결과 ID", example = "1")
        private Long id;

        @Schema(description = "측정 세션 ID", example = "34")
        private Long measurementSessionId;

        @Schema(description = "발 눌림 영역 세그먼테이션 결과 이미지 URL", example = "https://project5-42-oregon-feetfit-s3.s3.us-west-2.amazonaws.com/plantar-footprint/df4b3137-2432-4a85-a47d-1bb39a8dbf80.png")
        private String plantarFootprintImageUrl;

        @Schema(description = "기록 시각", example = "2026-08-17T18:20:00")
        private LocalDateTime recordedAt;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "좌우 발 압력 히트맵 이미지 저장 응답")
    public static class PressureHeatmapImageResultDTO {

        @Schema(description = "종합 발 분석 결과 ID", example = "1")
        private Long id;

        @Schema(description = "측정 세션 ID", example = "34")
        private Long measurementSessionId;

        @Schema(description = "왼발 압력 히트맵 이미지 URL", example = "https://project5-42-oregon-feetfit-s3.s3.us-west-2.amazonaws.com/pressure-heatmap-left/df4b3137-2432-4a85-a47d-1bb39a8dbf80.png")
        private String leftPressureHeatmapImageUrl;

        @Schema(description = "오른발 압력 히트맵 이미지 URL", example = "https://project5-42-oregon-feetfit-s3.s3.us-west-2.amazonaws.com/pressure-heatmap-right/e245c3c1-930c-41c1-8423-e60268fe3f17.png")
        private String rightPressureHeatmapImageUrl;

        @Schema(description = "왼발 압력 센서 측정 묶음 ID", example = "11")
        private Long leftPressureSensorReadingId;

        @Schema(description = "오른발 압력 센서 측정 묶음 ID", example = "12")
        private Long rightPressureSensorReadingId;

        @Schema(description = "저장된 압력 센서 개별 값 개수. 왼발 12개 + 오른발 12개", example = "24")
        private Integer pressureSensorValueCount;

        @Schema(description = "수정 시각", example = "2026-08-23T18:20:00")
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
                allowableValues = {"PRESSURE_BALANCE", "HALLUX_VALGUS", "ATHLETES_FOOT", "SKIN_IRRITATION", "FOOT_ENVIRONMENT"})
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

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "지표별 리포트 저장 응답")
    public static class SaveMetricResultResultDTO {

        @Schema(description = "리포트 ID", example = "1")
        private Long reportId;

        @Schema(description = "측정 세션 ID", example = "1")
        private Long measurementSessionId;

        @Schema(description = "저장된 지표 타입", example = "HALLUX_VALGUS")
        private MetricType savedMetricType;

        @Schema(description = "점수", example = "72.0")
        private Float score;

        @Schema(description = "지표 상태", example = "ATTENTION_NEEDED")
        private GaugeStatus status;

        @Schema(description = "어드바이스 목록 (2개)")
        private List<String> advice;

        @Schema(description = "5개 지표 모두 저장 완료 여부", example = "false")
        private boolean allMetricsComplete;

        @Schema(description = "종합 점수. 5개 지표 미완성 시 null", example = "80", nullable = true)
        private Integer totalScore;

        @Schema(description = "아직 저장되지 않은 지표 목록. 완료되면 빈 리스트", example = "[\"PRESSURE_BALANCE\", \"SKIN_IRRITATION\"]")
        private List<MetricType> missingMetrics;

        @Schema(description = "투두가 매칭된 건강 타입. 미완성 시 null", nullable = true)
        private List<HealthType> matchedHealthTypes;

        @Schema(description = "새로 매칭된 발 관리 투두 개수. 미완성 시 null", example = "3", nullable = true)
        private Integer matchedTodoCount;

        @Schema(description = "건강 뉴스가 매칭된 건강 타입. 미완성 시 null", nullable = true)
        private List<HealthType> matchedArticleHealthTypes;

        @Schema(description = "새로 매칭된 건강 뉴스 개수. 미완성 시 null", example = "4", nullable = true)
        private Integer matchedArticleCount;

        @Schema(description = "생성 시각", example = "2026-05-20T09:00:00")
        private LocalDateTime createdAt;

        @Schema(description = "수정 시각", example = "2026-05-20T09:00:00")
        private LocalDateTime updatedAt;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "측정 날짜 리스트 조회 응답")
    public static class MeasuredDateListResultDTO {

        @Schema(description = "측정한 날짜 리스트", example = "[\"2026-05-01\", \"2026-05-15\", \"2026-05-31\"]")
        private List<LocalDate> measuredDates;
    }
}
