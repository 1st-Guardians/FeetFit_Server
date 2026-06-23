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
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class ReportCommandServiceImpl implements ReportCommandService {

    private static final Set<MetricType> REQUIRED_METRIC_TYPES = EnumSet.of(
            MetricType.PRESSURE_BALANCE,
            MetricType.HALLUX_VALGUS,
            MetricType.ATHLETES_FOOT,
            MetricType.FOOT_ODOR,
            MetricType.FOOT_ENVIRONMENT
    );

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
            Long userId,
            ReportRequestDTO.SaveHalluxValgusDTO request,
            MultipartFile leftFootImage,
            MultipartFile rightFootImage) {

        MeasurementSession measurementSession = getValidatedTransferringMeasurementSession(
                userId, request.getMeasurementSessionId());

        ImageResponseDTO.UploadImageResultDTO leftImageUpload =
                imageUploadService.upload("hallux-valgus-left", leftFootImage);
        ImageResponseDTO.UploadImageResultDTO rightImageUpload =
                imageUploadService.upload("hallux-valgus-right", rightFootImage);

        String leftAnalysisText = ReportConverter.generateHvaAnalysisText(request.getLeftToeAngleDegree());
        String rightAnalysisText = ReportConverter.generateHvaAnalysisText(request.getRightToeAngleDegree());
        float riskScore = ReportConverter.calculateHvaRiskScore(
                request.getLeftToeAngleDegree(), request.getRightToeAngleDegree());

        HalluxValgusAnalysis saved = halluxValgusAnalysisRepository
                .findByMeasurementSessionId(measurementSession.getId())
                .map(existing -> {
                    existing.updateHalluxValgusAnalysis(
                            request.getLeftToeAngleDegree(), leftAnalysisText, leftImageUpload.getImageUrl(),
                            request.getRightToeAngleDegree(), rightAnalysisText, rightImageUpload.getImageUrl(),
                            riskScore, request.getScoreAnalysisText()
                    );
                    return existing;
                })
                .orElseGet(() -> halluxValgusAnalysisRepository.save(
                        ReportConverter.toHalluxValgusAnalysis(
                                measurementSession, request,
                                leftImageUpload.getImageUrl(), rightImageUpload.getImageUrl())
                ));

        return ReportConverter.toSaveHalluxValgusResultDTO(saved);
    }

    @Override
    public ReportResponseDTO.TinaPedisAnalysisResultDTO saveTinaPedisAnalysis(
            Long userId,
            ReportRequestDTO.SaveTinaPedisAnalysisDTO request,
            MultipartFile suspiciousAreaMapImage,
            MultipartFile originalFootImage
    ) {
        MeasurementSession measurementSession = getValidatedTransferringMeasurementSession(
                userId, request.getMeasurementSessionId());
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

    @Override
    public ReportResponseDTO.DailyFootAnalysisResultDTO saveDailyFootAnalysis(
            Long userId, ReportRequestDTO.SaveDailyFootAnalysisDTO request) {

        MeasurementSession measurementSession = getValidatedTransferringMeasurementSession(
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

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));

        DailyFootAnalysis previousAnalysis = dailyFootAnalysisRepository
                .findTopByMeasurementSessionUserIdAndCreatedAtLessThanOrderByCreatedAtDesc(
                        userId, saved.getCreatedAt().toLocalDate().atStartOfDay()
                )
                .orElse(null);

        return ReportConverter.toDailyFootAnalysisResultDTO(saved, user.getFootSize(), previousAnalysis);
    }

    @Override
    public ReportResponseDTO.SaveMetricResultResultDTO saveMetricResult(
            Long userId, ReportRequestDTO.SaveMetricResultDTO request) {

        MeasurementSession measurementSession = getValidatedTransferringMeasurementSession(
                userId, request.getMeasurementSessionId());

        // 측정 세션에 대한 Report를 찾거나 생성
        Report report = reportRepository.findByMeasurementSessionId(measurementSession.getId())
                .orElseGet(() -> reportRepository.save(
                        Report.builder()
                                .measurementSession(measurementSession)
                                .user(measurementSession.getUser())
                                .reportDate(LocalDateTime.now())
                                .totalScore(null)
                                .build()
                ));

        // 해당 metricType의 결과를 upsert
        MetricAnalysisResult metricResult = metricAnalysisResultRepository
                .findByReportIdAndMetricType(report.getId(), request.getMetricType())
                .map(existing -> {
                    existing.updateMetricResult(request.getScore(), request.getStatus(), request.getAdvice());
                    return existing;
                })
                .orElseGet(() -> metricAnalysisResultRepository.save(
                        MetricAnalysisResult.builder()
                                .report(report)
                                .metricType(request.getMetricType())
                                .score(request.getScore())
                                .status(request.getStatus())
                                .advice(request.getAdvice())
                                .build()
                ));

        // 현재 저장된 지표 목록 조회 (flush 후 재조회하여 정확성 보장)
        metricAnalysisResultRepository.flush();
        List<MetricAnalysisResult> allResults = metricAnalysisResultRepository.findByReportId(report.getId());

        Set<MetricType> savedTypes = allResults.stream()
                .map(MetricAnalysisResult::getMetricType)
                .collect(Collectors.toSet());

        List<MetricType> missingMetrics = REQUIRED_METRIC_TYPES.stream()
                .filter(type -> !savedTypes.contains(type))
                .sorted(Comparator.comparing(Enum::name))
                .collect(Collectors.toList());

        boolean allComplete = missingMetrics.isEmpty();
        Integer totalScore = null;
        List<HealthType> matchedHealthTypes = null;
        Integer matchedTodoCount = null;

        if (allComplete) {
            totalScore = calculateSimpleTotalScore(allResults);
            report.updateTotalScore(totalScore);
            report.updateReportDate(LocalDateTime.now());

            List<UserStretchingTodo> matchedTodos = replaceTodayTodoAssignments(
                    measurementSession.getUser(), allResults, LocalDate.now());
            matchedHealthTypes = matchedTodos.stream()
                    .map(UserStretchingTodo::getHealthType)
                    .collect(Collectors.collectingAndThen(
                            Collectors.toCollection(LinkedHashSet::new),
                            ArrayList::new
                    ));
            matchedTodoCount = matchedTodos.size();
        }

        return ReportConverter.toSaveMetricResultResultDTO(
                report, metricResult, allComplete, totalScore, missingMetrics, matchedHealthTypes, matchedTodoCount);
    }

    // 단순 평균 totalScore 계산 (5개 지표 단순 평균)
    static int calculateSimpleTotalScore(List<MetricAnalysisResult> results) {
        double avg = results.stream()
                .filter(r -> REQUIRED_METRIC_TYPES.contains(r.getMetricType()))
                .mapToDouble(r -> safeScore(r.getScore()))
                .average()
                .orElse(0.0);
        return (int) Math.round(avg);
    }

    private MeasurementSession getValidatedTransferringMeasurementSession(Long userId, Long measurementSessionId) {
        MeasurementSession measurementSession = measurementSessionRepository
                .findById(measurementSessionId)
                .orElseThrow(() -> new MeasurementHandler(ErrorStatus.MEASUREMENT_NOT_FOUND));

        if (!measurementSession.getUser().getId().equals(userId)) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_FORBIDDEN);
        }

        if (measurementSession.getStatus() == MeasurementStatus.MEASURING) {
            measurementSession.updateStatus(MeasurementStatus.TRANSFERRING, measurementSession.getMeasurementDurationSec());
        }

        if (!measurementSession.getStatus().equals(MeasurementStatus.TRANSFERRING)) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_NOT_TRANSFERRING);
        }

        return measurementSession;
    }

    private List<UserStretchingTodo> replaceTodayTodoAssignments(
            User user,
            List<MetricAnalysisResult> metricResults,
            LocalDate reportDate
    ) {
        LocalDateTime startOfDay = reportDate.atStartOfDay();
        LocalDateTime startOfNextDay = reportDate.plusDays(1).atStartOfDay();

        userStretchingTodoAssignmentRepository.deleteByUserIdAndTodoDateBetween(
                user.getId(), startOfDay, startOfNextDay);

        List<WeightedMetric> weightedMetrics = metricResults.stream()
                .map(result -> new WeightedMetric(
                        metricToHealthType(result.getMetricType()),
                        safeScore(result.getScore()),
                        metricWeight(result.getMetricType()),
                        weightedDeficit(result.getScore(), result.getMetricType())
                ))
                .sorted((l, r) -> Double.compare(r.weightedDeficit(), l.weightedDeficit()))
                .toList();

        Set<HealthType> healthTypes = weightedMetrics.stream()
                .map(WeightedMetric::healthType)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<HealthType, Queue<UserStretchingTodo>> todosByHealthType = new EnumMap<>(HealthType.class);
        userStretchingTodoRepository
                .findByHealthTypeInAndTodoDateGreaterThanEqualAndTodoDateLessThanOrderByIdAsc(
                        healthTypes, startOfDay, startOfNextDay)
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

    static float safeScore(Float score) {
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
