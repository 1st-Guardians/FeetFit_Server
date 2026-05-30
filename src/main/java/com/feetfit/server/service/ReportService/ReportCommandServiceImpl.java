package com.feetfit.server.service.ReportService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.MeasurementHandler;
import com.feetfit.server.apiPayload.exception.handler.UserHandler;
import com.feetfit.server.converter.ReportConverter;
import com.feetfit.server.domain.*;
import com.feetfit.server.domain.enums.HealthType;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.MetricType;
import com.feetfit.server.repository.*;
import com.feetfit.server.service.ImageService.ImageUploadService;
import com.feetfit.server.web.dto.image.ImageResponseDTO;
import com.feetfit.server.web.dto.report.ReportRequestDTO;
import com.feetfit.server.web.dto.report.ReportResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class ReportCommandServiceImpl implements ReportCommandService {

    private final HalluxValgusAnalysisRepository halluxValgusAnalysisRepository;
    private final MeasurementSessionRepository measurementSessionRepository;
    private final TinaPedisAnalysisRepository tinaPedisAnalysisRepository;
    private final DailyFootAnalysisRepository dailyFootAnalysisRepository;
    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final MetricAnalysisResultRepository metricAnalysisResultRepository;
    private final UserStretchingTodoRepository userStretchingTodoRepository;
    private final UserStretchingTodoAssignmentRepository userStretchingTodoAssignmentRepository;
    private final ImageUploadService imageUploadService;

    @Override
    public ReportResponseDTO.SaveHalluxValgusResultDTO saveHalluxValgusAnalysis(
            Long userId, ReportRequestDTO.SaveHalluxValgusDTO request) {

        MeasurementSession measurementSession = measurementSessionRepository
                .findById(request.getMeasurementSessionId())
                .orElseThrow(() -> new MeasurementHandler(ErrorStatus.MEASUREMENT_NOT_FOUND));

        // 본인 측정 세션인지 검증
        if (!measurementSession.getUser().getId().equals(userId)) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_FORBIDDEN);
        }

        // COMPLETED 상태인지 검증
        if (!measurementSession.getStatus().equals(MeasurementStatus.COMPLETED)) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_NOT_COMPLETED);
        }

        // 오늘 날짜에 이미 저장된 데이터 있는지 조회
        HalluxValgusAnalysis saved = halluxValgusAnalysisRepository
                .findByUserIdAndDate(userId, LocalDateTime.now())
                .map(existing -> {
                    // 있으면 UPDATE
                    existing.updateHalluxValgusAnalysis(
                            request.getImageUrl(),
                            request.getLeftToeAngleDegree(), request.getLeftAnalysisText(),
                            request.getRightToeAngleDegree(), request.getRightAnalysisText(),
                            request.getRiskScore(), request.getScoreAnalysisText()
                    );
                    return existing;
                })
                .orElseGet(() ->
                        // 없으면 INSERT
                        halluxValgusAnalysisRepository.save(
                                ReportConverter.toHalluxValgusAnalysis(measurementSession, request)
                        )
                );

        return ReportConverter.toSaveHalluxValgusResultDTO(saved);
    }

    @Override
    public ReportResponseDTO.TinaPedisAnalysisResultDTO saveTinaPedisAnalysis(
            Long userId,
            ReportRequestDTO.SaveTinaPedisAnalysisDTO request,
            MultipartFile suspiciousAreaMapImage,
            MultipartFile originalFootImage
    ) {
        MeasurementSession measurementSession = getValidatedCompletedMeasurementSession(
                userId,
                request.getMeasurementSessionId()
        );
        LocalDateTime recordedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        ImageResponseDTO.UploadImageResultDTO suspiciousAreaMapUpload =
                imageUploadService.upload("tina-pedis-map", suspiciousAreaMapImage);
        ImageResponseDTO.UploadImageResultDTO originalFootUpload =
                imageUploadService.upload("tina-pedis-original", originalFootImage);

        TinaPedisAnalysis saved = tinaPedisAnalysisRepository.findByMeasurementSessionId(measurementSession.getId())
                .map(existing -> {
                    existing.updateTinaPedisAnalysis(
                            request.getFungalSuspicionSafetyScore(),
                            request.getSkinReactionSafetyScore(),
                            request.getFungalSuspicionSafetyDescription(),
                            request.getSkinReactionSafetyDescription(),
                            request.getTotalScoreDescription(),
                            suspiciousAreaMapUpload.getImageUrl(),
                            originalFootUpload.getImageUrl(),
                            recordedAt
                    );
                    return existing;
                })
                .orElseGet(() -> tinaPedisAnalysisRepository.save(
                        ReportConverter.toTinaPedisAnalysis(
                                measurementSession,
                                request,
                                suspiciousAreaMapUpload.getImageUrl(),
                                originalFootUpload.getImageUrl(),
                                recordedAt
                        )
                ));

        tinaPedisAnalysisRepository.flush();

        TinaPedisAnalysis previousAnalysis = tinaPedisAnalysisRepository
                .findTopByMeasurementSessionUserIdAndRecordedAtLessThanOrderByRecordedAtDesc(
                        userId,
                        saved.getRecordedAt().toLocalDate().atStartOfDay()
                )
                .orElse(null);

        return ReportConverter.toTinaPedisAnalysisResultDTO(saved, previousAnalysis);
    }

    private MeasurementSession getValidatedCompletedMeasurementSession(Long userId, Long measurementSessionId) {
        MeasurementSession measurementSession = measurementSessionRepository
                .findById(measurementSessionId)
                .orElseThrow(() -> new MeasurementHandler(ErrorStatus.MEASUREMENT_NOT_FOUND));

        if (!measurementSession.getUser().getId().equals(userId)) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_FORBIDDEN);
        }

        if (!measurementSession.getStatus().equals(MeasurementStatus.COMPLETED)) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_NOT_COMPLETED);
        }

        return measurementSession;
    }

    @Override
    public ReportResponseDTO.DailyFootAnalysisResultDTO saveDailyFootAnalysis(
            Long userId, ReportRequestDTO.SaveDailyFootAnalysisDTO request) {

        MeasurementSession measurementSession = getValidatedCompletedMeasurementSession(
                userId, request.getMeasurementSessionId()
        );

        DailyFootAnalysis saved = dailyFootAnalysisRepository
                .findByMeasurementSessionId(measurementSession.getId())
                .map(existing -> {
                    existing.update(
                            request.getConditionLevel(),
                            request.getConditionComments(),
                            request.getBalanceScore(),
                            request.getBalanceComment(),
                            request.getLeftPressurePercent(),
                            request.getRightPressurePercent(),
                            request.getLeftPressureImageUrl(),
                            request.getRightPressureImageUrl(),
                            request.getMeasuredLeftFootSizeMm(),
                            request.getMeasuredRightFootSizeMm(),
                            request.getLeftFootWidthMm(),
                            request.getRightFootWidthMm(),
                            request.getFootOdourPpm(),
                            request.getFootOdourComment(),
                            request.getAvgTemperatureCelsius(),
                            request.getAvgHumidityPercent(),
                            request.getCareTips(),
                            request.getTypeText()
                    );
                    return existing;
                })
                .orElseGet(() -> dailyFootAnalysisRepository.save(
                        ReportConverter.toDailyFootAnalysis(measurementSession, request)
                ));

        // 유저 발 사이즈 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));

        // 이전 측정 데이터 조회
        DailyFootAnalysis previousAnalysis = dailyFootAnalysisRepository
                .findTopByMeasurementSessionUserIdAndCreatedAtLessThanOrderByCreatedAtDesc(
                        userId, saved.getCreatedAt().toLocalDate().atStartOfDay()
                )
                .orElse(null);

        return ReportConverter.toDailyFootAnalysisResultDTO(saved, user.getFootSize(), previousAnalysis);
    }

    @Override
    public ReportResponseDTO.SaveReportResultDTO saveReport(
            Long userId, ReportRequestDTO.SaveReportDTO request) {

        MeasurementSession measurementSession = getValidatedCompletedMeasurementSession(
                userId, request.getMeasurementSessionId());

        int totalScore = calculateWeightedTotalScore(request.getMetricAnalysisResults());

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().plusDays(1).atStartOfDay();

        Report report = reportRepository
                .findTopByUserIdAndReportDateGreaterThanEqualAndReportDateLessThanOrderByReportDateDesc(
                        userId, startOfDay, endOfDay)
                .map(existing -> {
                    existing.updateTotalScore(totalScore);
                    existing.updateReportDate(LocalDateTime.now());
                    existing.updateMeasurementSession(measurementSession);
                    return existing;
                })
                .orElseGet(() -> reportRepository.save(
                        Report.builder()
                                .measurementSession(measurementSession)
                                .user(measurementSession.getUser())
                                .reportDate(LocalDateTime.now())
                                .totalScore(totalScore)
                                .build()
                ));

        // 기존 MetricAnalysisResult 먼저 삭제
        metricAnalysisResultRepository.deleteByReportId(report.getId());
        metricAnalysisResultRepository.flush();  // ← DB에 즉시 반영

        // 새 MetricAnalysisResult 저장
        List<MetricAnalysisResult> metricAnalysisResults = request.getMetricAnalysisResults().stream()
                .map(dto -> ReportConverter.toMetricAnalysisResult(report, dto))
                .collect(Collectors.toList());

        metricAnalysisResultRepository.saveAll(metricAnalysisResults);

        // 연관관계 동기화
        report.getMetricAnalysisResults().clear();
        report.getMetricAnalysisResults().addAll(metricAnalysisResults);

        List<UserStretchingTodo> matchedTodos = replaceTodayTodoAssignments(
                measurementSession.getUser(),
                request.getMetricAnalysisResults(),
                LocalDate.now()
        );
        List<HealthType> matchedHealthTypes = matchedTodos.stream()
                .map(UserStretchingTodo::getHealthType)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        ArrayList::new
                ));

        return ReportConverter.toSaveReportResultDTO(report, matchedHealthTypes, matchedTodos.size());
    }

    private int calculateWeightedTotalScore(List<ReportRequestDTO.SaveMetricAnalysisResultDTO> metricAnalysisResults) {
        double weightedScoreSum = metricAnalysisResults.stream()
                .mapToDouble(dto -> safeScore(dto.getScore()) * metricWeight(dto.getMetricType()))
                .sum();
        double weightSum = metricAnalysisResults.stream()
                .mapToDouble(dto -> metricWeight(dto.getMetricType()))
                .sum();

        if (weightSum == 0.0) {
            return 0;
        }
        return Math.round((float) (weightedScoreSum / weightSum));
    }

    private List<UserStretchingTodo> replaceTodayTodoAssignments(
            User user,
            List<ReportRequestDTO.SaveMetricAnalysisResultDTO> metricAnalysisResults,
            LocalDate reportDate
    ) {
        LocalDateTime startOfDay = reportDate.atStartOfDay();
        LocalDateTime startOfNextDay = reportDate.plusDays(1).atStartOfDay();

        userStretchingTodoAssignmentRepository.deleteByUserIdAndTodoDateBetween(
                user.getId(),
                startOfDay,
                startOfNextDay
        );

        List<WeightedMetric> weightedMetrics = metricAnalysisResults.stream()
                .map(dto -> new WeightedMetric(
                        metricToHealthType(dto.getMetricType()),
                        safeScore(dto.getScore()),
                        metricWeight(dto.getMetricType()),
                        weightedDeficit(dto.getScore(), dto.getMetricType())
                ))
                .sorted((left, right) -> Double.compare(right.weightedDeficit(), left.weightedDeficit()))
                .toList();

        Set<HealthType> healthTypes = weightedMetrics.stream()
                .map(WeightedMetric::healthType)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<HealthType, Queue<UserStretchingTodo>> todosByHealthType = new EnumMap<>(HealthType.class);
        userStretchingTodoRepository
                .findByHealthTypeInAndTodoDateGreaterThanEqualAndTodoDateLessThanOrderByIdAsc(
                        healthTypes,
                        startOfDay,
                        startOfNextDay
                )
                .forEach(todo -> todosByHealthType
                        .computeIfAbsent(todo.getHealthType(), ignored -> new ArrayDeque<>())
                        .add(todo));

        List<UserStretchingTodo> selectedTodos = selectWeightedTodos(weightedMetrics, todosByHealthType, 3);
        if (selectedTodos.isEmpty()) {
            return selectedTodos;
        }

        List<UserStretchingTodoAssignment> assignments = selectedTodos.stream()
                .map(todo -> UserStretchingTodoAssignment.builder()
                        .user(user)
                        .stretchingTodo(todo)
                        .isCompleted(false)
                        .build())
                .toList();

        userStretchingTodoAssignmentRepository.saveAll(assignments);
        return selectedTodos;
    }

    private List<UserStretchingTodo> selectWeightedTodos(
            List<WeightedMetric> weightedMetrics,
            Map<HealthType, Queue<UserStretchingTodo>> todosByHealthType,
            int limit
    ) {
        Map<HealthType, Integer> selectedCountByHealthType = new EnumMap<>(HealthType.class);
        List<UserStretchingTodo> selectedTodos = new ArrayList<>();

        while (selectedTodos.size() < limit) {
            WeightedMetric selectedMetric = null;
            double selectedPriority = -1.0;

            for (WeightedMetric metric : weightedMetrics) {
                Queue<UserStretchingTodo> todos = todosByHealthType.get(metric.healthType());
                if (todos == null || todos.isEmpty()) {
                    continue;
                }

                int alreadySelectedCount = selectedCountByHealthType.getOrDefault(metric.healthType(), 0);
                double priority = metric.weightedDeficit() / (alreadySelectedCount + 1.0);
                if (selectedMetric == null || priority > selectedPriority) {
                    selectedMetric = metric;
                    selectedPriority = priority;
                }
            }

            if (selectedMetric == null) {
                break;
            }

            selectedTodos.add(todosByHealthType.get(selectedMetric.healthType()).poll());
            selectedCountByHealthType.merge(selectedMetric.healthType(), 1, Integer::sum);
        }

        return selectedTodos;
    }

    private double weightedDeficit(Float score, MetricType metricType) {
        return (100.0 - safeScore(score)) * metricWeight(metricType);
    }

    private float metricWeight(MetricType metricType) {
        return switch (metricType) {
            case FOOT_ENVIRONMENT -> 1.25f;
            case ATHLETES_FOOT -> 1.2f;
            case HALLUX_VALGUS -> 1.15f;
            case PRESSURE_BALANCE -> 1.1f;
            case FOOT_ODOR -> 1.0f;
        };
    }

    private HealthType metricToHealthType(MetricType metricType) {
        return switch (metricType) {
            case PRESSURE_BALANCE -> HealthType.POSTURE;
            case FOOT_ENVIRONMENT -> HealthType.FOOT_ENVIRONMENT;
            case ATHLETES_FOOT -> HealthType.ATHLETES_FOOT;
            case HALLUX_VALGUS -> HealthType.HALLUX_VALGUS;
            case FOOT_ODOR -> HealthType.FOOT_ODOR;
        };
    }

    private float safeScore(Float score) {
        if (score == null) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(100.0f, score));
    }

    private record WeightedMetric(
            HealthType healthType,
            float score,
            float weight,
            double weightedDeficit
    ) {
    }
}
