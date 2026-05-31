package com.feetfit.server.service.ReportService;

import com.feetfit.server.web.dto.report.ReportResponseDTO;

import java.time.LocalDate;

public interface ReportQueryService {
    ReportResponseDTO.HalluxValgusResultDTO getHalluxValgusAnalysis(Long userId, LocalDate date);

    ReportResponseDTO.TinaPedisAnalysisResultDTO getTinaPedisAnalysis(Long userId, LocalDate date);

    ReportResponseDTO.DailyFootAnalysisResultDTO getDailyFootAnalysis(Long userId, LocalDate date);

    ReportResponseDTO.FootTypeTextResultDTO getFootTypeText(Long userId);

    ReportResponseDTO.ReportSummaryResultDTO getReportSummary(Long userId);

    ReportResponseDTO.MeasuredDateListResultDTO getMeasuredDates(Long userId, int year, int month);
}
