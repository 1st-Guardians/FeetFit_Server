package com.feetfit.server.service.ReportService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.MeasurementHandler;
import com.feetfit.server.apiPayload.exception.handler.UserHandler;
import com.feetfit.server.converter.ReportConverter;
import com.feetfit.server.domain.*;
import com.feetfit.server.domain.enums.GaugeStatus;
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
            MetricType.SKIN_IRRITATION,
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
    private final HealthArticleRepository healthArticleRepository;
    private final UserHealthArticleRepository userHealthArticleRepository;
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

    // ─── 종합 발 분석 파트별 저장 ────────────────────────────────────────────────

    @Override
    public ReportResponseDTO.DailyFootAnalysisResultDTO saveConditionPart(
            Long userId, ReportRequestDTO.ConditionPartDTO request) {
        MeasurementSession session = getValidatedTransferringMeasurementSession(userId, request.getMeasurementSessionId());
        DailyFootAnalysis analysis = findOrCreate(session);
        analysis.updateCondition(request.getConditionLevel(), request.getConditionComments());
        return buildDailyFootAnalysisResult(userId, analysis);
    }

    @Override
    public ReportResponseDTO.DailyFootAnalysisResultDTO saveBalancePart(
            Long userId, ReportRequestDTO.BalancePartDTO request) {
        MeasurementSession session = getValidatedTransferringMeasurementSession(userId, request.getMeasurementSessionId());
        DailyFootAnalysis analysis = findOrCreate(session);
        analysis.updateBalance(request.getBalanceScore(), request.getBalanceComment());
        return buildDailyFootAnalysisResult(userId, analysis);
    }

    @Override
    public ReportResponseDTO.DailyFootAnalysisResultDTO savePressurePart(
            Long userId,
            ReportRequestDTO.PressurePartDTO request,
            MultipartFile leftPressureImage,
            MultipartFile rightPressureImage) {
        MeasurementSession session = getValidatedTransferringMeasurementSession(userId, request.getMeasurementSessionId());

        ImageResponseDTO.UploadImageResultDTO leftUpload =
                imageUploadService.upload("pressure-left", leftPressureImage);
        ImageResponseDTO.UploadImageResultDTO rightUpload =
                imageUploadService.upload("pressure-right", rightPressureImage);

        DailyFootAnalysis analysis = findOrCreate(session);
        analysis.updatePressure(request.getLeftPressurePercent(), request.getRightPressurePercent(),
                leftUpload.getImageUrl(), rightUpload.getImageUrl());
        return buildDailyFootAnalysisResult(userId, analysis);
    }

    @Override
    public ReportResponseDTO.DailyFootAnalysisResultDTO saveMetricsPart(
            Long userId, ReportRequestDTO.MetricsPartDTO request) {
        MeasurementSession session = getValidatedTransferringMeasurementSession(userId, request.getMeasurementSessionId());
        DailyFootAnalysis analysis = findOrCreate(session);
        analysis.updateMetrics(request.getMeasuredLeftFootSizeMm(), request.getMeasuredRightFootSizeMm(),
                request.getLeftFootWidthMm(), request.getRightFootWidthMm());
        return buildDailyFootAnalysisResult(userId, analysis);
    }

    @Override
    public ReportResponseDTO.DailyFootAnalysisResultDTO saveEnvironmentPart(
            Long userId, ReportRequestDTO.EnvironmentPartDTO request) {
        MeasurementSession session = getValidatedTransferringMeasurementSession(userId, request.getMeasurementSessionId());
        DailyFootAnalysis analysis = findOrCreate(session);
        analysis.updateEnvironment(request.getAvgTemperatureCelsius(), request.getAvgHumidityPercent());
        return buildDailyFootAnalysisResult(userId, analysis);
    }

    @Override
    public ReportResponseDTO.DailyFootAnalysisResultDTO saveCareTipsPart(
            Long userId, ReportRequestDTO.CareTipsPartDTO request) {
        MeasurementSession session = getValidatedTransferringMeasurementSession(userId, request.getMeasurementSessionId());
        DailyFootAnalysis analysis = findOrCreate(session);
        analysis.updateCareTips(request.getCareTips(), request.getTypeText());
        return buildDailyFootAnalysisResult(userId, analysis);
    }

    private DailyFootAnalysis findOrCreate(MeasurementSession session) {
        return dailyFootAnalysisRepository.findByMeasurementSessionId(session.getId())
                .orElseGet(() -> dailyFootAnalysisRepository.save(
                        DailyFootAnalysis.builder().measurementSession(session).build()
                ));
    }

    private ReportResponseDTO.DailyFootAnalysisResultDTO buildDailyFootAnalysisResult(
            Long userId, DailyFootAnalysis analysis) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));
        dailyFootAnalysisRepository.flush();
        DailyFootAnalysis previousAnalysis = dailyFootAnalysisRepository
                .findTopByMeasurementSessionUserIdAndCreatedAtLessThanOrderByCreatedAtDesc(
                        userId, analysis.getCreatedAt().toLocalDate().atStartOfDay())
                .orElse(null);
        return ReportConverter.toDailyFootAnalysisResultDTO(analysis, user.getFootSize(), previousAnalysis);
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
        GaugeStatus calculatedStatus = calculateGaugeStatus(request.getScore());
        MetricAnalysisResult metricResult = metricAnalysisResultRepository
                .findByReportIdAndMetricType(report.getId(), request.getMetricType())
                .map(existing -> {
                    existing.updateMetricResult(request.getScore(), calculatedStatus, request.getAdvice());
                    return existing;
                })
                .orElseGet(() -> metricAnalysisResultRepository.save(
                        MetricAnalysisResult.builder()
                                .report(report)
                                .metricType(request.getMetricType())
                                .score(request.getScore())
                                .status(calculatedStatus)
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
        List<HealthType> matchedArticleHealthTypes = null;
        Integer matchedArticleCount = null;

        if (allComplete) {
            totalScore = calculateSimpleTotalScore(allResults);
            report.updateTotalScore(totalScore);
            report.updateReportDate(LocalDateTime.now());
            // clearAutomatically = true 로 세션이 초기화되기 전에 명시적으로 flush
            reportRepository.saveAndFlush(report);
            String recommendationContext = buildRecommendationContext(measurementSession, allResults);

            List<UserStretchingTodo> matchedTodos = replaceTodayTodoAssignments(
                    measurementSession.getUser(), allResults, LocalDate.now(), recommendationContext);
            matchedHealthTypes = matchedTodos.stream()
                    .map(UserStretchingTodo::getHealthType)
                    .collect(Collectors.collectingAndThen(
                            Collectors.toCollection(LinkedHashSet::new),
                            ArrayList::new
                    ));
            matchedTodoCount = matchedTodos.size();

            List<HealthArticle> matchedArticles = replaceUserHealthArticles(
                    measurementSession.getUser(), allResults, recommendationContext);
            matchedArticleHealthTypes = matchedArticles.stream()
                    .map(HealthArticle::getHealthType)
                    .collect(Collectors.collectingAndThen(
                            Collectors.toCollection(LinkedHashSet::new),
                            ArrayList::new
                    ));
            matchedArticleCount = matchedArticles.size();
        }

        return ReportConverter.toSaveMetricResultResultDTO(
                report, metricResult, allComplete, totalScore, missingMetrics,
                matchedHealthTypes, matchedTodoCount, matchedArticleHealthTypes, matchedArticleCount);
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

    // score 0~100을 3등분하여 GaugeStatus 계산
    // 0 이상 ~ 100/3 미만  → NEED_IMPROVEMENT
    // 100/3 이상 ~ 200/3 미만 → ATTENTION_NEEDED
    // 200/3 이상 ~ 100 이하 → VERY_GOOD
    static GaugeStatus calculateGaugeStatus(Float score) {
        float s = safeScore(score);
        if (s < 100f / 3f) return GaugeStatus.NEED_IMPROVEMENT;
        if (s < 200f / 3f) return GaugeStatus.ATTENTION_NEEDED;
        return GaugeStatus.VERY_GOOD;
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
            LocalDate reportDate,
            String recommendationContext
    ) {
        LocalDateTime startOfDay = reportDate.atStartOfDay();
        LocalDateTime startOfNextDay = reportDate.plusDays(1).atStartOfDay();

        userStretchingTodoAssignmentRepository.deleteByUserIdAndCreatedAtBetween(
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
                .findByHealthTypeInOrderByIdAsc(healthTypes)
                .stream()
                .sorted(Comparator.<UserStretchingTodo>comparingDouble(
                        todo -> keywordSimilarityScore(
                                recommendationContext,
                                todo.getTitle(),
                                todo.getHealthType()
                        )
                ).reversed())
                .forEach(todo -> todosByHealthType
                        .computeIfAbsent(todo.getHealthType(), ignored -> new ArrayDeque<>())
                        .add(todo));

        List<UserStretchingTodo> selectedTodos = selectWeightedTodos(
                weightedMetrics,
                todosByHealthType,
                recommendationContext,
                3
        );
        selectedTodos = distinctTodosById(selectedTodos);
        if (selectedTodos.isEmpty()) {
            return selectedTodos;
        }

        Set<Long> selectedTodoIds = selectedTodos.stream()
                .map(UserStretchingTodo::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        userStretchingTodoAssignmentRepository.deleteByUserIdAndTodoIdIn(user.getId(), selectedTodoIds);

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

    private List<UserStretchingTodo> distinctTodosById(List<UserStretchingTodo> todos) {
        Set<Long> seenTodoIds = new LinkedHashSet<>();
        List<UserStretchingTodo> distinctTodos = new ArrayList<>();

        for (UserStretchingTodo todo : todos) {
            if (seenTodoIds.add(todo.getId())) {
                distinctTodos.add(todo);
            }
        }

        return distinctTodos;
    }

    private List<HealthArticle> replaceUserHealthArticles(
            User user,
            List<MetricAnalysisResult> metricResults,
            String recommendationContext
    ) {
        userHealthArticleRepository.deleteByUserId(user.getId());

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

        Map<HealthType, Queue<HealthArticle>> articlesByHealthType = new EnumMap<>(HealthType.class);
        healthArticleRepository.findByHealthTypeInOrderByPublishedAtDesc(healthTypes)
                .stream()
                .sorted(Comparator.<HealthArticle>comparingDouble(
                        article -> keywordSimilarityScore(
                                recommendationContext,
                                articleContent(article),
                                article.getHealthType()
                        )
                ).reversed())
                .forEach(article -> articlesByHealthType
                        .computeIfAbsent(article.getHealthType(), ignored -> new ArrayDeque<>())
                        .add(article));

        List<HealthArticle> selectedArticles = selectWeightedArticles(
                weightedMetrics,
                articlesByHealthType,
                recommendationContext,
                4
        );
        if (selectedArticles.isEmpty()) {
            return selectedArticles;
        }

        List<UserHealthArticle> userHealthArticles = selectedArticles.stream()
                .map(article -> UserHealthArticle.builder()
                        .user(user)
                        .healthArticle(article)
                        .build())
                .toList();

        userHealthArticleRepository.saveAll(userHealthArticles);
        return selectedArticles;
    }

    private List<UserStretchingTodo> selectWeightedTodos(
            List<WeightedMetric> weightedMetrics,
            Map<HealthType, Queue<UserStretchingTodo>> todosByHealthType,
            String recommendationContext,
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
                UserStretchingTodo candidate = todos.peek();
                int alreadySelectedCount = selectedCountByHealthType.getOrDefault(metric.healthType(), 0);
                double keywordScore = keywordSimilarityScore(
                        recommendationContext,
                        candidate.getTitle(),
                        candidate.getHealthType()
                );
                double priority = recommendationPriority(metric.weightedDeficit(), keywordScore, alreadySelectedCount);
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

    private List<HealthArticle> selectWeightedArticles(
            List<WeightedMetric> weightedMetrics,
            Map<HealthType, Queue<HealthArticle>> articlesByHealthType,
            String recommendationContext,
            int limit
    ) {
        Map<HealthType, Integer> selectedCountByHealthType = new EnumMap<>(HealthType.class);
        List<HealthArticle> selectedArticles = new ArrayList<>();

        while (selectedArticles.size() < limit) {
            WeightedMetric selectedMetric = null;
            double selectedPriority = -1.0;

            for (WeightedMetric metric : weightedMetrics) {
                Queue<HealthArticle> articles = articlesByHealthType.get(metric.healthType());
                if (articles == null || articles.isEmpty()) {
                    continue;
                }
                HealthArticle candidate = articles.peek();
                int alreadySelectedCount = selectedCountByHealthType.getOrDefault(metric.healthType(), 0);
                double keywordScore = keywordSimilarityScore(
                        recommendationContext,
                        articleContent(candidate),
                        candidate.getHealthType()
                );
                double priority = recommendationPriority(metric.weightedDeficit(), keywordScore, alreadySelectedCount);
                if (selectedMetric == null || priority > selectedPriority) {
                    selectedMetric = metric;
                    selectedPriority = priority;
                }
            }

            if (selectedMetric == null) {
                break;
            }

            selectedArticles.add(articlesByHealthType.get(selectedMetric.healthType()).poll());
            selectedCountByHealthType.merge(selectedMetric.healthType(), 1, Integer::sum);
        }

        return selectedArticles;
    }

    private double recommendationPriority(double weightedDeficit, double keywordScore, int alreadySelectedCount) {
        double blendedScore = weightedDeficit * 0.65 + keywordScore * 0.35;
        return blendedScore / (alreadySelectedCount + 1.0);
    }

    private String buildRecommendationContext(
            MeasurementSession measurementSession,
            List<MetricAnalysisResult> metricResults
    ) {
        StringBuilder context = new StringBuilder();

        metricResults.forEach(result -> {
            appendText(context, result.getMetricType().name());
            appendText(context, result.getStatus().name());
            appendTexts(context, result.getAdvice());
        });

        halluxValgusAnalysisRepository.findByMeasurementSessionId(measurementSession.getId())
                .ifPresent(analysis -> {
                    appendText(context, analysis.getLeftAnalysisText());
                    appendText(context, analysis.getRightAnalysisText());
                    appendText(context, analysis.getScoreAnalysisText());
                });

        tinaPedisAnalysisRepository.findByMeasurementSessionId(measurementSession.getId())
                .ifPresent(analysis -> {
                    appendText(context, analysis.getFungalSuspicionSafetyDescription());
                    appendText(context, analysis.getSkinReactionSafetyDescription());
                    appendText(context, analysis.getTotalScoreDescription());
                });

        dailyFootAnalysisRepository.findByMeasurementSessionId(measurementSession.getId())
                .ifPresent(analysis -> {
                    appendTexts(context, analysis.getConditionComments());
                    appendText(context, analysis.getBalanceComment());
                    appendText(context, analysis.getFootOdourComment());
                    appendTexts(context, analysis.getCareTips());
                    appendText(context, analysis.getTypeText());
                });

        return normalizeText(context.toString());
    }

    private double keywordSimilarityScore(String contextText, String contentText, HealthType healthType) {
        String normalizedContext = normalizeText(contextText);
        String normalizedContent = normalizeText(contentText);

        if (normalizedContext.isBlank() || normalizedContent.isBlank()) {
            return 0.0;
        }

        double score = 0.0;
        boolean hasActiveKeywordGroup = false;

        for (List<String> keywordGroup : keywordGroups(healthType)) {
            boolean contextMatched = containsAny(normalizedContext, keywordGroup);
            boolean contentMatched = containsAny(normalizedContent, keywordGroup);

            if (contextMatched) {
                hasActiveKeywordGroup = true;
            }
            if (contextMatched && contentMatched) {
                score += 22.0;
            } else if (contentMatched) {
                score += 5.0;
            }

            for (String keyword : keywordGroup) {
                if (normalizedContext.contains(keyword) && normalizedContent.contains(keyword)) {
                    score += 6.0;
                }
            }
        }

        if (!hasActiveKeywordGroup && containsAnyHealthKeyword(normalizedContent, healthType)) {
            score += 10.0;
        }

        score += tokenOverlapScore(normalizedContext, normalizedContent);
        return Math.min(score, 100.0);
    }

    private double tokenOverlapScore(String contextText, String contentText) {
        Set<String> contextTokens = tokens(contextText);
        if (contextTokens.isEmpty()) {
            return 0.0;
        }

        long overlapCount = tokens(contentText).stream()
                .filter(contextTokens::contains)
                .limit(8)
                .count();
        return overlapCount * 2.0;
    }

    private Set<String> tokens(String text) {
        return Arrays.stream(text.split("[^0-9a-zA-Z가-힣]+"))
                .filter(token -> token.length() >= 2)
                .collect(Collectors.toSet());
    }

    private boolean containsAnyHealthKeyword(String text, HealthType healthType) {
        return keywordGroups(healthType).stream()
                .anyMatch(group -> containsAny(text, group));
    }

    private boolean containsAny(String text, List<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }

    private List<List<String>> keywordGroups(HealthType healthType) {
        return switch (healthType) {
            case ATHLETES_FOOT -> List.of(
                    List.of("진균", "곰팡이", "무좀균", "감염", "백선", "발가락사이"),
                    List.of("발적", "염증", "자극", "따가", "가려", "붉"),
                    List.of("습기", "습도", "땀", "통풍", "건조", "말리", "양말", "신발"),
                    List.of("각질", "갈라", "벗겨", "피부", "발바닥")
            );
            case HALLUX_VALGUS -> List.of(
                    List.of("무지외반", "엄지", "발가락", "각도", "변형", "돌출"),
                    List.of("통증", "염증", "붓", "압박", "마찰"),
                    List.of("교정", "스트레칭", "운동", "테이핑", "보조기")
            );
            case SKIN_IRRITATION -> List.of(
                    List.of("피부", "자극", "발적", "염증", "따가", "가려", "붉"),
                    List.of("각질", "갈라", "벗겨", "건조", "보습"),
                    List.of("마찰", "압박", "신발", "양말", "소재")
            );
            case POSTURE -> List.of(
                    List.of("자세", "균형", "압력", "체중", "보행", "걸음", "좌우"),
                    List.of("아치", "평발", "발바닥", "지지", "충격"),
                    List.of("종아리", "발목", "스트레칭", "운동", "근력")
            );
            case FOOT_ENVIRONMENT -> List.of(
                    List.of("온도", "습도", "습기", "땀", "통풍", "건조"),
                    List.of("신발", "깔창", "양말", "환기", "세탁"),
                    List.of("피부", "관리", "위생", "청결")
            );
        };
    }

    private String articleContent(HealthArticle article) {
        return normalizeText(article.getTitle() + " " + nullToBlank(article.getDescription()));
    }

    private String normalizeText(String text) {
        return nullToBlank(text)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .trim();
    }

    private void appendText(StringBuilder builder, String text) {
        if (text != null && !text.isBlank()) {
            builder.append(' ').append(text);
        }
    }

    private void appendTexts(StringBuilder builder, List<String> texts) {
        if (texts == null) {
            return;
        }
        texts.forEach(text -> appendText(builder, text));
    }

    private String nullToBlank(String text) {
        return text == null ? "" : text;
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
            case SKIN_IRRITATION -> 1.0f;
        };
    }

    private HealthType metricToHealthType(MetricType metricType) {
        return switch (metricType) {
            case PRESSURE_BALANCE -> HealthType.POSTURE;
            case FOOT_ENVIRONMENT -> HealthType.FOOT_ENVIRONMENT;
            case ATHLETES_FOOT -> HealthType.ATHLETES_FOOT;
            case HALLUX_VALGUS -> HealthType.HALLUX_VALGUS;
            case SKIN_IRRITATION -> HealthType.SKIN_IRRITATION;
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
