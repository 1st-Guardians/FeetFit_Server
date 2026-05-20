package com.feetfit.server.service.ReportService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.ReportHandler;
import com.feetfit.server.converter.ReportConverter;
import com.feetfit.server.domain.HalluxValgusAnalysis;
import com.feetfit.server.repository.HalluxValgusAnalysisRepository;
import com.feetfit.server.web.dto.report.ReportResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReportQueryServiceImpl implements ReportQueryService {

    private final HalluxValgusAnalysisRepository halluxValgusAnalysisRepository;

    @Override
    public ReportResponseDTO.HalluxValgusResultDTO getHalluxValgusAnalysis(Long userId, LocalDate date) {

        // 해당 날짜 가장 최근 데이터 조회
        HalluxValgusAnalysis halluxValgusAnalysis = halluxValgusAnalysisRepository
                .findLatestByUserIdAndDate(userId, date)
                .orElseThrow(() -> new ReportHandler(ErrorStatus.REPORT_NOT_FOUND));

        // 이전 측정 데이터 조회 (없으면 null)
        HalluxValgusAnalysis previousAnalysis = halluxValgusAnalysisRepository
                .findPreviousByUserIdBeforeDate(userId, date)
                .orElse(null);

        return ReportConverter.toHalluxValgusResultDTO(halluxValgusAnalysis, previousAnalysis);
    }
}