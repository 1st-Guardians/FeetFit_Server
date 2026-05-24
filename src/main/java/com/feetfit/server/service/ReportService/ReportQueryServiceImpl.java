package com.feetfit.server.service.ReportService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.ReportHandler;
import com.feetfit.server.apiPayload.exception.handler.UserHandler;
import com.feetfit.server.converter.ReportConverter;
import com.feetfit.server.domain.DailyFootAnalysis;
import com.feetfit.server.domain.HalluxValgusAnalysis;
import com.feetfit.server.domain.TinaPedisAnalysis;
import com.feetfit.server.domain.User;
import com.feetfit.server.repository.DailyFootAnalysisRepository;
import com.feetfit.server.repository.HalluxValgusAnalysisRepository;
import com.feetfit.server.repository.TinaPedisAnalysisRepository;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.web.dto.report.ReportResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReportQueryServiceImpl implements ReportQueryService {

    private final HalluxValgusAnalysisRepository halluxValgusAnalysisRepository;
    private final TinaPedisAnalysisRepository tinaPedisAnalysisRepository;
    private final UserRepository userRepository;
    private final DailyFootAnalysisRepository dailyFootAnalysisRepository;

    @Override
    public ReportResponseDTO.HalluxValgusResultDTO getHalluxValgusAnalysis(Long userId, LocalDate date) {

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        // 해당 날짜 가장 최근 데이터 조회
        HalluxValgusAnalysis halluxValgusAnalysis = halluxValgusAnalysisRepository
                .findTopByMeasurementSessionUserIdAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThanOrderByUpdatedAtDesc(
                        userId, startOfDay, endOfDay)
                .orElseThrow(() -> new ReportHandler(ErrorStatus.REPORT_NOT_FOUND));

        // 이전 측정 데이터 조회 (없으면 null)
        HalluxValgusAnalysis previousAnalysis = halluxValgusAnalysisRepository
                .findTopByMeasurementSessionUserIdAndUpdatedAtLessThanOrderByUpdatedAtDesc(
                        userId, startOfDay)
                .orElse(null);

        return ReportConverter.toHalluxValgusResultDTO(halluxValgusAnalysis, previousAnalysis);
    }

    @Override
    public ReportResponseDTO.TinaPedisAnalysisResultDTO getTinaPedisAnalysis(Long userId, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        TinaPedisAnalysis tinaPedisAnalysis = tinaPedisAnalysisRepository
                .findTopByMeasurementSessionUserIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtDesc(
                        userId,
                        startOfDay,
                        endOfDay
                )
                .orElseThrow(() -> new ReportHandler(ErrorStatus.TINA_PEDIS_ANALYSIS_NOT_FOUND));

        TinaPedisAnalysis previousAnalysis = tinaPedisAnalysisRepository
                .findTopByMeasurementSessionUserIdAndRecordedAtLessThanOrderByRecordedAtDesc(
                        userId,
                        startOfDay
                )
                .orElse(null);

        return ReportConverter.toTinaPedisAnalysisResultDTO(tinaPedisAnalysis, previousAnalysis);
    }

    @Override
    public ReportResponseDTO.DailyFootAnalysisResultDTO getDailyFootAnalysis(Long userId, LocalDate date) {

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        DailyFootAnalysis analysis = dailyFootAnalysisRepository
                .findTopByMeasurementSessionUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                        userId, startOfDay, endOfDay)
                .orElseThrow(() -> new ReportHandler(ErrorStatus.REPORT_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));

        DailyFootAnalysis previousAnalysis = dailyFootAnalysisRepository
                .findTopByMeasurementSessionUserIdAndCreatedAtLessThanOrderByCreatedAtDesc(
                        userId, startOfDay)
                .orElse(null);

        return ReportConverter.toDailyFootAnalysisResultDTO(analysis, user.getFootSize(), previousAnalysis);
    }
}
