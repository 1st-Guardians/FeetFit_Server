package com.feetfit.server.service.ReportService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.GeneralException;
import com.feetfit.server.apiPayload.exception.handler.ReportHandler;
import com.feetfit.server.apiPayload.exception.handler.UserHandler;
import com.feetfit.server.converter.ReportConverter;
import com.feetfit.server.domain.*;
import com.feetfit.server.domain.enums.MetricType;
import com.feetfit.server.repository.*;
import com.feetfit.server.web.dto.report.ReportResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReportQueryServiceImpl implements ReportQueryService {

    private static final Set<MetricType> REQUIRED_METRIC_TYPES = EnumSet.of(
            MetricType.PRESSURE_BALANCE,
            MetricType.HALLUX_VALGUS,
            MetricType.ATHLETES_FOOT,
            MetricType.SKIN_IRRITATION,
            MetricType.FOOT_ENVIRONMENT
    );

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

        HalluxValgusAnalysis halluxValgusAnalysis = halluxValgusAnalysisRepository
                .findTopByMeasurementSessionUserIdAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThanOrderByUpdatedAtDesc(
                        userId, startOfDay, endOfDay)
                .orElseThrow(() -> new ReportHandler(ErrorStatus.REPORT_NOT_FOUND));

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
                        userId, startOfDay, endOfDay)
                .orElseThrow(() -> new ReportHandler(ErrorStatus.TINA_PEDIS_ANALYSIS_NOT_FOUND));

        TinaPedisAnalysis previousAnalysis = tinaPedisAnalysisRepository
                .findTopByMeasurementSessionUserIdAndRecordedAtLessThanOrderByRecordedAtDesc(
                        userId, startOfDay)
                .orElse(null);

        return ReportConverter.toTinaPedisAnalysisResultDTO(tinaPedisAnalysis, previousAnalysis);
    }

    @Override
    public ReportResponseDTO.DailyFootAnalysisResultDTO getDailyFootAnalysis(Long userId, LocalDate date) {

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

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

        return ReportConverter.toDailyFootAnalysisResultDTO(
                analysis, user.getFootSize(), previousAnalysis);
    }

    @Override
    public ReportResponseDTO.FootTypeTextResultDTO getFootTypeText(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));

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

        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

        Report report = reportRepository
                .findTopByUserIdAndReportDateGreaterThanEqualAndReportDateLessThanOrderByReportDateDesc(
                        userId, startOfDay, endOfDay)
                .orElseThrow(() -> new ReportHandler(ErrorStatus.REPORT_NOT_FOUND));

        // 5개 필수 지표가 모두 저장되었는지 확인
        List<MetricAnalysisResult> results = report.getMetricAnalysisResults();
        Set<MetricType> savedTypes = results.stream()
                .map(MetricAnalysisResult::getMetricType)
                .collect(Collectors.toSet());

        List<MetricType> missingMetrics = REQUIRED_METRIC_TYPES.stream()
                .filter(type -> !savedTypes.contains(type))
                .sorted(Comparator.comparing(Enum::name))
                .collect(Collectors.toList());

        if (!missingMetrics.isEmpty()) {
            String missingNames = missingMetrics.stream()
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            throw new GeneralException(
                    ErrorStatus.REPORT_METRIC_INCOMPLETE,
                    "모든 지표가 저장되지 않아 종합 리포트를 조회할 수 없습니다. 누락된 지표: [" + missingNames + "]"
            );
        }

        // 백엔드에서 5개 지표 단순 평균으로 totalScore 계산
        int totalScore = ReportCommandServiceImpl.calculateSimpleTotalScore(results);

        // 완료된 리포트만 월별 점수에 포함
        YearMonth currentMonth = YearMonth.from(today);
        YearMonth startMonth = currentMonth.minusMonths(11);

        LocalDateTime startDateTime = startMonth.atDay(1).atStartOfDay();
        LocalDateTime endDateTime = currentMonth.plusMonths(1).atDay(1).atStartOfDay();

        List<Report> yearlyReports = reportRepository.findByUserIdAndReportDateBetween(
                userId, startDateTime, endDateTime);

        List<ReportResponseDTO.MonthlyScoreDTO> monthlyScores = yearlyReports.stream()
                .filter(r -> isCompleteReport(r.getMetricAnalysisResults()))
                .collect(Collectors.groupingBy(
                        r -> YearMonth.from(r.getReportDate()),
                        Collectors.averagingDouble(r ->
                                ReportCommandServiceImpl.calculateSimpleTotalScore(r.getMetricAnalysisResults())
                        )
                ))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> ReportResponseDTO.MonthlyScoreDTO.builder()
                        .month(entry.getKey().getMonthValue())
                        .avgScore(Math.round(entry.getValue() * 10) / 10.0f)
                        .build())
                .collect(Collectors.toList());

        return ReportConverter.toReportSummaryResultDTO(totalScore, results, monthlyScores);
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

    private boolean isCompleteReport(List<MetricAnalysisResult> results) {
        Set<MetricType> types = results.stream()
                .map(MetricAnalysisResult::getMetricType)
                .collect(Collectors.toSet());
        return REQUIRED_METRIC_TYPES.stream().allMatch(types::contains);
    }
}
