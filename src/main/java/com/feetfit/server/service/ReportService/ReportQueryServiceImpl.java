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
import java.time.YearMonth;
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
    private final MeasurementSessionRepository measurementSessionRepository;

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
    public ReportResponseDTO.ReportSummaryResultDTO getReportSummary(Long userId) {

        userRepository.findById(userId)
                .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));

        // 오늘 날짜 기준
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

        Report report = reportRepository
                .findTopByUserIdAndReportDateGreaterThanEqualAndReportDateLessThanOrderByReportDateDesc(
                        userId, startOfDay, endOfDay)
                .orElseThrow(() -> new ReportHandler(ErrorStatus.REPORT_NOT_FOUND));

        // 이번 달을 포함한 최근 12개월
        YearMonth currentMonth = YearMonth.from(today);
        YearMonth startMonth = currentMonth.minusMonths(11);

        LocalDateTime startDateTime = startMonth.atDay(1).atStartOfDay();
        LocalDateTime endDateTime = currentMonth.plusMonths(1).atDay(1).atStartOfDay();

        List<Report> yearlyReports = reportRepository.findByUserIdAndReportDateBetween(
                userId, startDateTime, endDateTime);

        List<ReportResponseDTO.MonthlyScoreDTO> monthlyScores = yearlyReports.stream()
                .collect(Collectors.groupingBy(
                        r -> YearMonth.from(r.getReportDate()),
                        Collectors.averagingDouble(r ->
                                r.getMetricAnalysisResults().stream()
                                        .mapToDouble(MetricAnalysisResult::getScore)
                                        .average()
                                        .orElse(0.0)
                        )
                ))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> ReportResponseDTO.MonthlyScoreDTO.builder()
                        .month(entry.getKey().getMonthValue())
                        .avgScore(Math.round(entry.getValue() * 10) / 10.0f)
                        .build())
                .collect(Collectors.toList());

        return ReportConverter.toReportSummaryResultDTO(report, monthlyScores);
    }

    @Override
    public ReportResponseDTO.MeasuredDateListResultDTO getMeasuredDates(Long userId, int year, int month) {

        userRepository.findById(userId)
                .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));

        List<LocalDate> measuredDates = measurementSessionRepository
                .findMeasuredDatesByYearAndMonth(userId, year, month)
                .stream()
                .map(LocalDate::parse)
                .collect(Collectors.toList());

        return ReportConverter.toMeasuredDateListResultDTO(measuredDates);
    }
}
