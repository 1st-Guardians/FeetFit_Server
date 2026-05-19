package com.feetfit.server.service.ReportService;

import com.feetfit.server.web.dto.report.ReportRequestDTO;
import com.feetfit.server.web.dto.report.ReportResponseDTO;

public interface ReportCommandService {

    // 무지외반 측정 분석 결과 저장
    ReportResponseDTO.SaveHalluxValgusResultDTO saveHalluxValgusAnalysis(
            Long userId, ReportRequestDTO.SaveHalluxValgusDTO request);
}