package com.feetfit.server.converter;

import com.feetfit.server.domain.HalluxValgusAnalysis;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.web.dto.report.ReportRequestDTO;
import com.feetfit.server.web.dto.report.ReportResponseDTO;

public class ReportConverter {

    // DTO → Entity
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

    // Entity → ResponseDTO
    public static ReportResponseDTO.SaveHalluxValgusResultDTO toSaveHalluxValgusResultDTO(
            HalluxValgusAnalysis analysis) {

        return ReportResponseDTO.SaveHalluxValgusResultDTO.builder()
                .id(analysis.getId())
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
                .build();
    }
}