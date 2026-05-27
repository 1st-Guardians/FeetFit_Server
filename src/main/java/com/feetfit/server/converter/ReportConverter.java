package com.feetfit.server.converter;

import com.feetfit.server.domain.*;
import com.feetfit.server.domain.enums.HealthType;
import com.feetfit.server.web.dto.report.ReportRequestDTO;
import com.feetfit.server.web.dto.report.ReportResponseDTO;

import java.util.List;
import java.util.stream.Collectors;

public class ReportConverter {

    public static HalluxValgusAnalysis toHalluxValgusAnalysis(
            MeasurementSession measurementSession,
            ReportRequestDTO.SaveHalluxValgusDTO request) {

        return HalluxValgusAnalysis.builder()
                .measurementSession(measurementSession)
                .imageUrl(request.getImageUrl())
                .leftToeAngleDegree(request.getLeftToeAngleDegree())
                .leftRiskLevel(request.getLeftRiskLevel())
                .leftAnalysisText(request.getLeftAnalysisText())
                .rightToeAngleDegree(request.getRightToeAngleDegree())
                .rightRiskLevel(request.getRightRiskLevel())
                .rightAnalysisText(request.getRightAnalysisText())
                .riskScore(request.getRiskScore())
                .scoreAnalysisText(request.getScoreAnalysisText())
                .build();
    }

    public static ReportResponseDTO.SaveHalluxValgusResultDTO toSaveHalluxValgusResultDTO(
            HalluxValgusAnalysis analysis) {

        return ReportResponseDTO.SaveHalluxValgusResultDTO.builder()
                .id(analysis.getId())
                .measurementSessionId(analysis.getMeasurementSession().getId())
                .imageUrl(analysis.getImageUrl())
                .leftToeAngleDegree(analysis.getLeftToeAngleDegree())
                .leftRiskLevel(analysis.getLeftRiskLevel())
                .leftAnalysisText(analysis.getLeftAnalysisText())
                .rightToeAngleDegree(analysis.getRightToeAngleDegree())
                .rightRiskLevel(analysis.getRightRiskLevel())
                .rightAnalysisText(analysis.getRightAnalysisText())
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
                .imageUrl(analysis.getImageUrl())
                .leftToeAngleDegree(analysis.getLeftToeAngleDegree())
                .leftRiskLevel(analysis.getLeftRiskLevel())
                .leftAnalysisText(analysis.getLeftAnalysisText())
                .rightToeAngleDegree(analysis.getRightToeAngleDegree())
                .rightRiskLevel(analysis.getRightRiskLevel())
                .rightAnalysisText(analysis.getRightAnalysisText())
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
            String originalFootImageUrl
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
                .recordedAt(request.getRecordedAt())
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
            ReportRequestDTO.SaveDailyFootAnalysisDTO request) {
        return DailyFootAnalysis.builder()
                .measurementSession(measurementSession)
                .conditionLevel(request.getConditionLevel())
                .conditionComments(request.getConditionComments())
                .balanceScore(request.getBalanceScore())
                .balanceComment(request.getBalanceComment())
                .leftPressurePercent(request.getLeftPressurePercent())
                .rightPressurePercent(request.getRightPressurePercent())
                .leftPressureImageUrl(request.getLeftPressureImageUrl())
                .rightPressureImageUrl(request.getRightPressureImageUrl())
                .measuredLeftFootSizeMm(request.getMeasuredLeftFootSizeMm())
                .measuredRightFootSizeMm(request.getMeasuredRightFootSizeMm())
                .leftFootWidthMm(request.getLeftFootWidthMm())
                .rightFootWidthMm(request.getRightFootWidthMm())
                .footOdourPpm(request.getFootOdourPpm())
                .footOdourComment(request.getFootOdourComment())
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
            Report report,
            List<ReportResponseDTO.MonthlyScoreDTO> monthlyScores) {

        List<ReportResponseDTO.MetricScoreDTO> metricScoreDTOs = report.getMetricAnalysisResults().stream()
                .map(ReportConverter::toMetricScoreDTO)
                .collect(Collectors.toList());

        return ReportResponseDTO.ReportSummaryResultDTO.builder()
                .totalScore(report.getTotalScore())
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

    public static ReportResponseDTO.SaveReportResultDTO toSaveReportResultDTO(
            Report report,
            List<HealthType> matchedHealthTypes,
            Integer matchedTodoCount
    ) {

        List<ReportResponseDTO.MetricScoreDTO> metricScoreDTOs = report.getMetricAnalysisResults().stream()
                .map(ReportConverter::toMetricScoreDTO)
                .collect(Collectors.toList());

        return ReportResponseDTO.SaveReportResultDTO.builder()
                .id(report.getId())
                .measurementSessionId(report.getMeasurementSession().getId())
                .totalScore(report.getTotalScore())
                .metricAnalysisResults(metricScoreDTOs)
                .matchedHealthTypes(matchedHealthTypes)
                .matchedTodoCount(matchedTodoCount)
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .build();
    }
}
