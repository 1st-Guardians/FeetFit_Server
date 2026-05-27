package com.feetfit.server.service.ReportService;

import com.feetfit.server.web.dto.report.ReportRequestDTO;
import com.feetfit.server.web.dto.report.ReportResponseDTO;
import org.springframework.web.multipart.MultipartFile;

public interface ReportCommandService {

    // 무지외반 측정 분석 결과 저장
    ReportResponseDTO.SaveHalluxValgusResultDTO saveHalluxValgusAnalysis(
            Long userId, ReportRequestDTO.SaveHalluxValgusDTO request);

    // 무좀 분석 결과 저장
    ReportResponseDTO.TinaPedisAnalysisResultDTO saveTinaPedisAnalysis(
            Long userId,
            ReportRequestDTO.SaveTinaPedisAnalysisDTO request,
            MultipartFile suspiciousAreaMapImage,
            MultipartFile originalFootImage
    );

    // 종합 측정 분석 결과 저장
    ReportResponseDTO.DailyFootAnalysisResultDTO saveDailyFootAnalysis(
            Long userId, ReportRequestDTO.SaveDailyFootAnalysisDTO request);

    // 요약 결과 저장
    ReportResponseDTO.SaveReportResultDTO saveReport(Long userId, ReportRequestDTO.SaveReportDTO request);
}
