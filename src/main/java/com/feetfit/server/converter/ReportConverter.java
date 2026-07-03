package com.feetfit.server.converter;

import com.feetfit.server.domain.*;
import com.feetfit.server.domain.enums.HealthType;
import com.feetfit.server.web.dto.report.ReportRequestDTO;
import com.feetfit.server.web.dto.report.ReportResponseDTO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class ReportConverter {

    // HVA 각도 기반 분석 텍스트 생성
    /*
    - **1단계: 정상 범위** (15도 미만)
        엄지발가락이 두 번째 발가락 쪽으로 기울어진 각도(HVA)가 00°로 측정되었습니다. 정상 기준(15° 이하)에 해당합니다.
    - **2단계: 경미한 변형 의심** (15~20도)
        엄지발가락이 두 번째 발가락 쪽으로 기울어진 각도(HVA)가 00°로 측정되었습니다. 경미한 변형 범위(15~20°)에 해당합니다.
    - **3단계: 변형 진행 단계** (20~40도)
        엄지발가락이 두 번째 발가락 쪽으로 기울어진 각도(HVA)가 00°로 측정되었습니다. 변형이 진행된 범위(20~40°)에 해당합니다.
    - **4단계: 심한 변형 단계** (40도 이상)
        엄지발가락이 두 번째 발가락 쪽으로 기울어진 각도(HVA)가 00°로 측정되었습니다. 심한 변형 범위(40° 이상)에 해당합니다.
    */
    public static String generateHvaAnalysisText(Float toeAngleDegree) {
        if (toeAngleDegree == null) return null;
        if (toeAngleDegree <= 15) {
            return String.format(
                    "엄지발가락이 두 번째 발가락 쪽으로 기울어진 각도(HVA)가 %.1f°로 측정되었습니다. 정상 기준(15° 이하)에 해당합니다.",
                    toeAngleDegree);
        } else if (toeAngleDegree <= 20) {
            return String.format(
                    "엄지발가락이 두 번째 발가락 쪽으로 기울어진 각도(HVA)가 %.1f°로 측정되었습니다. 경미한 변형 범위(15~20°)에 해당합니다.",
                    toeAngleDegree);
        } else if (toeAngleDegree <= 40) {
            return String.format(
                    "엄지발가락이 두 번째 발가락 쪽으로 기울어진 각도(HVA)가 %.1f°로 측정되었습니다. 변형이 진행된 범위(20~40°)에 해당합니다.",
                    toeAngleDegree);
        } else {
            return String.format(
                    "엄지발가락이 두 번째 발가락 쪽으로 기울어진 각도(HVA)가 %.1f°로 측정되었습니다. 심한 변형 범위(40° 이상)에 해당합니다.",
                    toeAngleDegree);
        }
    }

    // riskScore 계산: Score = max(0, 100 - 2.5 × HVA)
    // 왼발/오른발 평균으로 계산
    public static float calculateHvaRiskScore(Float left, Float right) {
        float leftScore = left != null ? Math.max(0, 100 - 2.5f * left) : 100f;
        float rightScore = right != null ? Math.max(0, 100 - 2.5f * right) : 100f;
        return (leftScore + rightScore) / 2f;
    }

    public static HalluxValgusAnalysis toHalluxValgusAnalysis(
            MeasurementSession measurementSession,
            ReportRequestDTO.SaveHalluxValgusDTO request,
            String leftImageUrl,
            String rightImageUrl) {

        String leftAnalysisText = generateHvaAnalysisText(request.getLeftToeAngleDegree());
        String rightAnalysisText = generateHvaAnalysisText(request.getRightToeAngleDegree());
        float riskScore = calculateHvaRiskScore(request.getLeftToeAngleDegree(), request.getRightToeAngleDegree());

        return HalluxValgusAnalysis.builder()
                .measurementSession(measurementSession)
                .leftToeAngleDegree(request.getLeftToeAngleDegree())
                .leftAnalysisText(leftAnalysisText)
                .leftImageUrl(leftImageUrl)
                .rightToeAngleDegree(request.getRightToeAngleDegree())
                .rightAnalysisText(rightAnalysisText)
                .rightImageUrl(rightImageUrl)
                .riskScore(riskScore)
                .scoreAnalysisText(request.getScoreAnalysisText())
                .build();
    }

    public static ReportResponseDTO.SaveHalluxValgusResultDTO toSaveHalluxValgusResultDTO(
            HalluxValgusAnalysis analysis) {
        return ReportResponseDTO.SaveHalluxValgusResultDTO.builder()
                .id(analysis.getId())
                .measurementSessionId(analysis.getMeasurementSession().getId())
                .leftToeAngleDegree(analysis.getLeftToeAngleDegree())
                .leftAnalysisText(analysis.getLeftAnalysisText())
                .leftImageUrl(analysis.getLeftImageUrl())
                .rightToeAngleDegree(analysis.getRightToeAngleDegree())
                .rightAnalysisText(analysis.getRightAnalysisText())
                .rightImageUrl(analysis.getRightImageUrl())
                .riskScore(analysis.getRiskScore())
                .scoreAnalysisText(analysis.getScoreAnalysisText())
                .createdAt(analysis.getCreatedAt())
                .updatedAt(analysis.getUpdatedAt())
                .build();
    }

    public static ReportResponseDTO.HalluxValgusResultDTO toHalluxValgusResultDTO(
            HalluxValgusAnalysis analysis,
            HalluxValgusAnalysis previousAnalysis) {

        Float previousRiskScore = previousAnalysis != null ? previousAnalysis.getRiskScore() : null;
        Float riskScoreDiff = previousRiskScore != null
                ? analysis.getRiskScore() - previousRiskScore
                : null;

        return ReportResponseDTO.HalluxValgusResultDTO.builder()
                .id(analysis.getId())
                .measurementSessionId(analysis.getMeasurementSession().getId())
                .leftToeAngleDegree(analysis.getLeftToeAngleDegree())
                .leftAnalysisText(analysis.getLeftAnalysisText())
                .leftImageUrl(analysis.getLeftImageUrl())
                .rightToeAngleDegree(analysis.getRightToeAngleDegree())
                .rightAnalysisText(analysis.getRightAnalysisText())
                .rightImageUrl(analysis.getRightImageUrl())
                .riskScore(analysis.getRiskScore())
                .scoreAnalysisText(analysis.getScoreAnalysisText())
                .previousRiskScore(previousRiskScore)
                .riskScoreDiff(riskScoreDiff)
                .createdAt(analysis.getCreatedAt())
                .updatedAt(analysis.getUpdatedAt())
                .build();
    }

    public static TinaPedisAnalysis toTinaPedisAnalysis(
            MeasurementSession measurementSession,
            ReportRequestDTO.SaveTinaPedisAnalysisDTO request,
            String suspiciousAreaMapImageUrl,
            String originalFootImageUrl,
            LocalDateTime recordedAt
    ) {
        return TinaPedisAnalysis.builder()
                .measurementSession(measurementSession)
                .fungalSuspicionSafetyScore(request.getFungalSuspicionSafetyScore())
                .skinReactionSafetyScore(request.getSkinReactionSafetyScore())
                .fungalSuspicionSafetyDescription(request.getFungalSuspicionSafetyDescription())
                .skinReactionSafetyDescription(request.getSkinReactionSafetyDescription())
                .totalScoreDescription(request.getTotalScoreDescription())
                .suspiciousAreaMapImageUrl(suspiciousAreaMapImageUrl)
                .originalFootImageUrl(originalFootImageUrl)
                .recordedAt(recordedAt)
                .build();
    }

    public static ReportResponseDTO.TinaPedisAnalysisResultDTO toTinaPedisAnalysisResultDTO(
            TinaPedisAnalysis analysis
    ) {
        return toTinaPedisAnalysisResultDTO(analysis, null);
    }

    public static ReportResponseDTO.TinaPedisAnalysisResultDTO toTinaPedisAnalysisResultDTO(
            TinaPedisAnalysis analysis,
            TinaPedisAnalysis previousAnalysis
    ) {
        Float totalScore = calculateTinaPedisTotalScore(analysis);
        Float previousTotalScore = previousAnalysis != null
                ? calculateTinaPedisTotalScore(previousAnalysis)
                : null;
        Float totalScoreDiff = previousTotalScore != null
                ? roundToOneDecimal(totalScore - previousTotalScore)
                : null;

        return ReportResponseDTO.TinaPedisAnalysisResultDTO.builder()
                .id(analysis.getId())
                .measurementSessionId(analysis.getMeasurementSession().getId())
                .fungalSuspicionSafetyScore(analysis.getFungalSuspicionSafetyScore())
                .skinReactionSafetyScore(analysis.getSkinReactionSafetyScore())
                .totalScore(totalScore)
                .previousTotalScore(previousTotalScore)
                .totalScoreDiff(totalScoreDiff)
                .fungalSuspicionSafetyDescription(analysis.getFungalSuspicionSafetyDescription())
                .skinReactionSafetyDescription(analysis.getSkinReactionSafetyDescription())
                .totalScoreDescription(analysis.getTotalScoreDescription())
                .suspiciousAreaMapImageUrl(analysis.getSuspiciousAreaMapImageUrl())
                .originalFootImageUrl(analysis.getOriginalFootImageUrl())
                .recordedAt(analysis.getRecordedAt())
                .createdAt(analysis.getCreatedAt())
                .updatedAt(analysis.getUpdatedAt())
                .build();
    }

    private static Float calculateTinaPedisTotalScore(TinaPedisAnalysis analysis) {
        return roundToOneDecimal(
                analysis.getFungalSuspicionSafetyScore() * 0.7f
                        + analysis.getSkinReactionSafetyScore() * 0.3f
        );
    }

    private static Float roundToOneDecimal(Float value) {
        return Math.round(value * 10f) / 10f;
    }

    public static DailyFootAnalysis toDailyFootAnalysis(
            MeasurementSession measurementSession,
            ReportRequestDTO.SaveDailyFootAnalysisDTO request,
            String leftPressureImageUrl,
            String rightPressureImageUrl,
            com.feetfit.server.service.FootOdourService.FootOdourCalculator.FootOdourResult odour) {
        return DailyFootAnalysis.builder()
                .measurementSession(measurementSession)
                .conditionLevel(request.getConditionLevel())
                .conditionComments(request.getConditionComments())
                .balanceScore(request.getBalanceScore())
                .balanceComment(request.getBalanceComment())
                .leftPressurePercent(request.getLeftPressurePercent())
                .rightPressurePercent(request.getRightPressurePercent())
                .leftPressureImageUrl(leftPressureImageUrl)
                .rightPressureImageUrl(rightPressureImageUrl)
                .measuredLeftFootSizeMm(request.getMeasuredLeftFootSizeMm())
                .measuredRightFootSizeMm(request.getMeasuredRightFootSizeMm())
                .leftFootWidthMm(request.getLeftFootWidthMm())
                .rightFootWidthMm(request.getRightFootWidthMm())
                .tvocPpb(request.getTvocPpb())
                .baselineTvocPpb(request.getBaselineTvocPpb())
                .rawFootOdourPpm(odour != null ? odour.rawPpm() : null)
                .footOdourPpm(odour != null ? odour.displayPpm() : null)
                .footOdourComment(odour != null ? odour.comment() : null)
                .avgTemperatureCelsius(request.getAvgTemperatureCelsius())
                .avgHumidityPercent(request.getAvgHumidityPercent())
                .careTips(request.getCareTips())
                .typeText(request.getTypeText())
                .build();
    }

    public static ReportResponseDTO.DailyFootAnalysisResultDTO toDailyFootAnalysisResultDTO(
            DailyFootAnalysis analysis,
            Integer userFootSize,
            DailyFootAnalysis previousAnalysis) {

        // 이전 측정 대비 균형 점수 변화
        Float balanceScoreDiff = previousAnalysis != null
                ? analysis.getBalanceScore() - previousAnalysis.getBalanceScore()
                : null;

        // 입력 사이즈 대비 측정 사이즈 변화
        Float leftFootSizeDiff = (userFootSize != null && analysis.getMeasuredLeftFootSizeMm() != null)
                ? analysis.getMeasuredLeftFootSizeMm() - userFootSize
                : null;

        Float rightFootSizeDiff = (userFootSize != null && analysis.getMeasuredRightFootSizeMm() != null)
                ? analysis.getMeasuredRightFootSizeMm() - userFootSize
                : null;

        return ReportResponseDTO.DailyFootAnalysisResultDTO.builder()
                .id(analysis.getId())
                .measurementSessionId(analysis.getMeasurementSession().getId())
                .conditionLevel(analysis.getConditionLevel())
                .conditionComments(analysis.getConditionComments())
                .balanceScore(analysis.getBalanceScore())
                .balanceComment(analysis.getBalanceComment())
                .balanceScoreDiff(balanceScoreDiff)
                .leftPressurePercent(analysis.getLeftPressurePercent())
                .rightPressurePercent(analysis.getRightPressurePercent())
                .leftPressureImageUrl(analysis.getLeftPressureImageUrl())
                .rightPressureImageUrl(analysis.getRightPressureImageUrl())
                .userFootSize(userFootSize)
                .measuredLeftFootSizeMm(analysis.getMeasuredLeftFootSizeMm())
                .measuredRightFootSizeMm(analysis.getMeasuredRightFootSizeMm())
                .leftFootSizeDiff(leftFootSizeDiff)
                .rightFootSizeDiff(rightFootSizeDiff)
                .leftFootWidthMm(analysis.getLeftFootWidthMm())
                .rightFootWidthMm(analysis.getRightFootWidthMm())
                .footOdourPpm(analysis.getFootOdourPpm())
                .footOdourComment(analysis.getFootOdourComment())
                .avgTemperatureCelsius(analysis.getAvgTemperatureCelsius())
                .avgHumidityPercent(analysis.getAvgHumidityPercent())
                .careTips(analysis.getCareTips())
                .typeText(analysis.getTypeText())
                .createdAt(analysis.getCreatedAt())
                .updatedAt(analysis.getUpdatedAt())
                .build();
    }

    public static ReportResponseDTO.FootTypeTextResultDTO toFootTypeTextResultDTO(
            String nickname, String typeText) {
        return ReportResponseDTO.FootTypeTextResultDTO.builder()
                .nickname(nickname)
                .typeText(typeText)
                .build();
    }

    public static ReportResponseDTO.MetricScoreDTO toMetricScoreDTO(MetricAnalysisResult result) {
        return ReportResponseDTO.MetricScoreDTO.builder()
                .metricType(result.getMetricType())
                .score(result.getScore())
                .status(result.getStatus())
                .advice(result.getAdvice())
                .build();
    }

    public static ReportResponseDTO.MonthlyScoreDTO toMonthlyScoreDTO(Object[] row) {
        return ReportResponseDTO.MonthlyScoreDTO.builder()
                .month(((Number) row[0]).intValue())
                .avgScore(((Number) row[1]).floatValue())
                .build();
    }

    public static ReportResponseDTO.ReportSummaryResultDTO toReportSummaryResultDTO(
            int totalScore,
            List<MetricAnalysisResult> results,
            List<ReportResponseDTO.MonthlyScoreDTO> monthlyScores) {

        List<ReportResponseDTO.MetricScoreDTO> metricScoreDTOs = results.stream()
                .map(ReportConverter::toMetricScoreDTO)
                .collect(Collectors.toList());

        return ReportResponseDTO.ReportSummaryResultDTO.builder()
                .totalScore(totalScore)
                .metricScores(metricScoreDTOs)
                .monthlyScores(monthlyScores)
                .build();
    }

    public static MetricAnalysisResult toMetricAnalysisResult(
            Report report,
            ReportRequestDTO.SaveMetricAnalysisResultDTO dto) {
        return MetricAnalysisResult.builder()
                .report(report)
                .metricType(dto.getMetricType())
                .score(dto.getScore())
                .status(dto.getStatus())
                .advice(dto.getAdvice())
                .build();
    }

    public static ReportResponseDTO.SaveMetricResultResultDTO toSaveMetricResultResultDTO(
            Report report,
            MetricAnalysisResult saved,
            boolean allComplete,
            Integer totalScore,
            List<com.feetfit.server.domain.enums.MetricType> missingMetrics,
            List<HealthType> matchedHealthTypes,
            Integer matchedTodoCount,
            List<HealthType> matchedArticleHealthTypes,
            Integer matchedArticleCount
    ) {
        return ReportResponseDTO.SaveMetricResultResultDTO.builder()
                .reportId(report.getId())
                .measurementSessionId(report.getMeasurementSession().getId())
                .savedMetricType(saved.getMetricType())
                .score(saved.getScore())
                .status(saved.getStatus())
                .advice(saved.getAdvice())
                .allMetricsComplete(allComplete)
                .totalScore(totalScore)
                .missingMetrics(missingMetrics)
                .matchedHealthTypes(matchedHealthTypes)
                .matchedTodoCount(matchedTodoCount)
                .matchedArticleHealthTypes(matchedArticleHealthTypes)
                .matchedArticleCount(matchedArticleCount)
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .build();
    }

    public static ReportResponseDTO.MeasuredDateListResultDTO toMeasuredDateListResultDTO(
            List<LocalDate> measuredDates) {
        return ReportResponseDTO.MeasuredDateListResultDTO.builder()
                .measuredDates(measuredDates)
                .build();
    }
}
