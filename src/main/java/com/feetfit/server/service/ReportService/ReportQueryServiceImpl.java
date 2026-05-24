package com.feetfit.server.service.ReportService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.ReportHandler;
import com.feetfit.server.apiPayload.exception.handler.UserHandler;
import com.feetfit.server.converter.ReportConverter;
import com.feetfit.server.domain.*;
import com.feetfit.server.repository.*;
import com.feetfit.server.web.dto.report.ReportResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReportQueryServiceImpl implements ReportQueryService {

    private final HalluxValgusAnalysisRepository halluxValgusAnalysisRepository;
    private final TinaPedisAnalysisRepository tinaPedisAnalysisRepository;
    private final UserRepository userRepository;
    private final DailyFootAnalysisRepository dailyFootAnalysisRepository;
    private final ReportRepository reportRepository;

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

        // user 조회 먼저
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));

        DailyFootAnalysis analysis = dailyFootAnalysisRepository
                .findTopByMeasurementSessionUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                        userId, startOfDay, endOfDay)
                .orElseThrow(() -> new ReportHandler(ErrorStatus.REPORT_NOT_FOUND));

        DailyFootAnalysis previousAnalysis = dailyFootAnalysisRepository
                .findTopByMeasurementSessionUserIdAndCreatedAtLessThanOrderByCreatedAtDesc(
                        userId, startOfDay)
                .orElse(null);

        return ReportConverter.toDailyFootAnalysisResultDTO(analysis, user.getFootSize(), previousAnalysis);
    }

    @Override
    public ReportResponseDTO.FootTypeTextResultDTO getFootTypeText(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));

        // 가장 최근 DailyFootAnalysis의 typeText 조회 (없으면 null)
        String typeText = dailyFootAnalysisRepository
                .findTopByMeasurementSessionUserIdOrderByCreatedAtDesc(userId)
                .map(DailyFootAnalysis::getTypeText)
                .orElse(null);

        return ReportConverter.toFootTypeTextResultDTO(user.getNickname(), typeText);
    }

    @Override
    public ReportResponseDTO.ReportSummaryResultDTO getReportSummary(Long userId, LocalDate date) {

        userRepository.findById(userId)
                .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        Report report = reportRepository
                .findTopByUserIdAndReportDateGreaterThanEqualAndReportDateLessThanOrderByReportDateDesc(
                        userId, startOfDay, endOfDay)
                .orElseThrow(() -> new ReportHandler(ErrorStatus.REPORT_NOT_FOUND));

        // 1년간 리포트 목록 조회
        LocalDateTime oneYearAgo = date.minusYears(1).atStartOfDay();
        List<Report> yearlyReports = reportRepository.findByUserIdAndReportDateBetween(
                userId, oneYearAgo, endOfDay);

        // 월별 평균 점수 계산 (서버에서 직접 계산)
        List<ReportResponseDTO.MonthlyScoreDTO> monthlyScores = yearlyReports.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getReportDate().getMonthValue(),
                        Collectors.averagingDouble(r ->
                                r.getMetricAnalysisResults().stream()
                                        .mapToDouble(m -> m.getScore())
                                        .average()
                                        .orElse(0.0)
                        )
                ))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> ReportResponseDTO.MonthlyScoreDTO.builder()
                        .month(entry.getKey())
                        .avgScore(Math.round(entry.getValue() * 10) / 10.0f)  // 소수점 1자리 반올림
                        .build())
                .collect(Collectors.toList());

        return ReportConverter.toReportSummaryResultDTO(report, monthlyScores);
    }
}
