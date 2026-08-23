package com.feetfit.server.service.ReportService;

import com.feetfit.server.web.dto.report.ReportRequestDTO;
import com.feetfit.server.web.dto.report.ReportResponseDTO;
import org.springframework.web.multipart.MultipartFile;

public interface ReportCommandService {

    // 무지외반 측정 분석 결과 저장
    ReportResponseDTO.SaveHalluxValgusResultDTO saveHalluxValgusAnalysis(
            Long userId,
            ReportRequestDTO.SaveHalluxValgusDTO request,
            MultipartFile leftFootImage,
            MultipartFile rightFootImage
    );

    // 무좀 분석 결과 저장
    ReportResponseDTO.TinaPedisAnalysisResultDTO saveTinaPedisAnalysis(
            Long userId,
            ReportRequestDTO.SaveTinaPedisAnalysisDTO request,
            MultipartFile suspiciousAreaMapImage,
            MultipartFile originalFootImage,
            MultipartFile soleSuspiciousAreaMapImage,
            MultipartFile soleOriginalFootImage
    );

    // 종합 발 분석 — 파트별 저장
    ReportResponseDTO.DailyFootAnalysisResultDTO saveConditionPart(
            Long userId, ReportRequestDTO.ConditionPartDTO request);

    ReportResponseDTO.DailyFootAnalysisResultDTO saveBalancePart(
            Long userId, ReportRequestDTO.BalancePartDTO request);

    ReportResponseDTO.DailyFootAnalysisResultDTO savePressurePart(
            Long userId,
            ReportRequestDTO.PressurePartDTO request,
            MultipartFile leftPressureImage,
            MultipartFile rightPressureImage);

    ReportResponseDTO.PressureHeatmapImageResultDTO savePressureHeatmapImage(
            Long userId,
            ReportRequestDTO.PressureHeatmapImageDTO request,
            MultipartFile leftPressureHeatmapImage,
            MultipartFile rightPressureHeatmapImage);

    ReportResponseDTO.PlantarFootprintImageResultDTO savePlantarFootprintImage(
            Long userId,
            ReportRequestDTO.PlantarFootprintImageDTO request,
            MultipartFile leftPlantarFootprintImage,
            MultipartFile rightPlantarFootprintImage);

    ReportResponseDTO.DailyFootAnalysisResultDTO saveMetricsPart(
            Long userId, ReportRequestDTO.MetricsPartDTO request);

    ReportResponseDTO.DailyFootAnalysisResultDTO saveEnvironmentPart(
            Long userId, ReportRequestDTO.EnvironmentPartDTO request);

    ReportResponseDTO.DailyFootAnalysisResultDTO saveCareTipsPart(
            Long userId, ReportRequestDTO.CareTipsPartDTO request);

    // 지표별 리포트 개별 저장 (단일 지표 upsert)
    ReportResponseDTO.SaveMetricResultResultDTO saveMetricResult(
            Long userId, ReportRequestDTO.SaveMetricResultDTO request);
}
