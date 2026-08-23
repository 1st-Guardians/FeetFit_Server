package com.feetfit.server.service.ShoeService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.MeasurementHandler;
import com.feetfit.server.apiPayload.exception.handler.ShoeHandler;
import com.feetfit.server.domain.DailyFootAnalysis;
import com.feetfit.server.domain.HalluxValgusAnalysis;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.PressureSensorReading;
import com.feetfit.server.domain.Shoe;
import com.feetfit.server.domain.ShoeLabMeasurement;
import com.feetfit.server.domain.ShoeLabMetric;
import com.feetfit.server.domain.ShoeRecommendation;
import com.feetfit.server.domain.ShoeRecommendationReason;
import com.feetfit.server.domain.ShoeReview;
import com.feetfit.server.domain.StaticPressureAnalysis;
import com.feetfit.server.domain.TinaPedisAnalysis;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.ShoeReviewSource;
import com.feetfit.server.repository.DailyFootAnalysisRepository;
import com.feetfit.server.repository.HalluxValgusAnalysisRepository;
import com.feetfit.server.repository.MeasurementSessionRepository;
import com.feetfit.server.repository.PressureSensorReadingRepository;
import com.feetfit.server.repository.ShoeLabMeasurementRepository;
import com.feetfit.server.repository.ShoeLabMetricRepository;
import com.feetfit.server.repository.ShoeRecommendationReasonRepository;
import com.feetfit.server.repository.ShoeRecommendationRepository;
import com.feetfit.server.repository.ShoeRepository;
import com.feetfit.server.repository.ShoeReviewRepository;
import com.feetfit.server.repository.StaticPressureAnalysisRepository;
import com.feetfit.server.repository.TinaPedisAnalysisRepository;
import com.feetfit.server.web.dto.shoe.ShoeAnalysisResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShoeAnalysisQueryServiceImpl implements ShoeAnalysisQueryService {

    private final MeasurementSessionRepository measurementSessionRepository;
    private final DailyFootAnalysisRepository dailyFootAnalysisRepository;
    private final TinaPedisAnalysisRepository tinaPedisAnalysisRepository;
    private final HalluxValgusAnalysisRepository halluxValgusAnalysisRepository;
    private final StaticPressureAnalysisRepository staticPressureAnalysisRepository;
    private final PressureSensorReadingRepository pressureSensorReadingRepository;
    private final ShoeRepository shoeRepository;
    private final ShoeReviewRepository shoeReviewRepository;
    private final ShoeLabMeasurementRepository shoeLabMeasurementRepository;
    private final ShoeLabMetricRepository shoeLabMetricRepository;
    private final ShoeRecommendationRepository shoeRecommendationRepository;
    private final ShoeRecommendationReasonRepository shoeRecommendationReasonRepository;

    @Override
    public ShoeAnalysisResponseDTO.RecommendationContext getRecommendationContext(
            Long userId, Long measurementSessionId, int page, int size) {
        MeasurementSession session = getOwnedCompletedSession(userId, measurementSessionId);
        Page<Shoe> shoePage = shoeRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id")));

        DailyFootAnalysis daily = dailyFootAnalysisRepository
                .findByMeasurementSessionId(measurementSessionId).orElse(null);
        TinaPedisAnalysis tinea = tinaPedisAnalysisRepository
                .findByMeasurementSessionId(measurementSessionId).orElse(null);
        HalluxValgusAnalysis hallux = halluxValgusAnalysisRepository
                .findByMeasurementSessionId(measurementSessionId).orElse(null);

        List<ShoeAnalysisResponseDTO.StaticPressureAnalysisItem> staticPressure =
                staticPressureAnalysisRepository
                        .findByMeasurementSessionIdOrderByFootSideAsc(measurementSessionId)
                        .stream().map(this::toStaticPressure).toList();
        List<ShoeAnalysisResponseDTO.PressureSensorReadingItem> sensorReadings =
                pressureSensorReadingRepository
                        .findByMeasurementSessionIdOrderByFootSideAscSensorIndexAsc(measurementSessionId)
                        .stream().map(this::toPressureReading).toList();

        List<Long> shoeIds = shoePage.getContent().stream().map(Shoe::getId).toList();
        Map<Long, List<ShoeReview>> reviewsByShoe = shoeIds.isEmpty()
                ? Collections.emptyMap()
                : shoeReviewRepository.findByShoeIdInAndSourceOrderByIdAsc(
                                shoeIds, ShoeReviewSource.MUSINSA).stream()
                        .collect(Collectors.groupingBy(review -> review.getShoe().getId()));
        List<ShoeLabMeasurement> pageLabs = shoeIds.isEmpty()
                ? List.of()
                : shoeLabMeasurementRepository
                        .findByShoeIdInAndSourceOrderByCapturedAtDescIdDesc(shoeIds, "RUNREPEAT");
        Map<Long, List<ShoeLabMeasurement>> labsByShoe = pageLabs.stream()
                .collect(Collectors.groupingBy(lab -> lab.getShoe().getId()));
        List<Long> measurementIds = pageLabs.stream().map(ShoeLabMeasurement::getId).toList();
        Map<Long, List<ShoeLabMetric>> metricsByMeasurement = measurementIds.isEmpty()
                ? Collections.emptyMap()
                : shoeLabMetricRepository.findByLabMeasurementIdInOrderByIdAsc(measurementIds).stream()
                        .collect(Collectors.groupingBy(metric -> metric.getLabMeasurement().getId()));

        List<ShoeAnalysisResponseDTO.ShoeAnalysisItem> shoes = shoePage.getContent().stream()
                .map(shoe -> toShoeAnalysisItem(
                        shoe,
                        reviewsByShoe.getOrDefault(shoe.getId(), List.of()),
                        labsByShoe.getOrDefault(shoe.getId(), List.of()),
                        metricsByMeasurement))
                .toList();

        return ShoeAnalysisResponseDTO.RecommendationContext.builder()
                .measurementSessionId(session.getId())
                .userId(userId)
                .measurementStatus(session.getStatus())
                .footState(ShoeAnalysisResponseDTO.FootState.builder()
                        .dailyFootAnalysis(toDaily(daily))
                        .tinaPedisAnalysis(toTinea(tinea))
                        .halluxValgusAnalysis(toHallux(hallux))
                        .staticPressureAnalyses(staticPressure)
                        .pressureSensorReadings(sensorReadings)
                        .build())
                .shoes(shoes)
                .currentPage(shoePage.getNumber())
                .totalPages(shoePage.getTotalPages())
                .totalElements(shoePage.getTotalElements())
                .hasNext(shoePage.hasNext())
                .build();
    }

    @Override
    public ShoeAnalysisResponseDTO.RecommendationSummaryContext getRecommendationSummaryContext(
            Long userId, Long shoeId) {
        ShoeRecommendation recommendation = shoeRecommendationRepository
                .findByUserIdAndShoeId(userId, shoeId)
                .orElseThrow(() -> new ShoeHandler(ErrorStatus.SHOE_RECOMMENDATION_NOT_FOUND));
        Shoe shoe = recommendation.getShoe();
        List<ShoeAnalysisResponseDTO.SavedReasonItem> reasons =
                shoeRecommendationReasonRepository.findByShoeRecommendationId(recommendation.getId())
                        .stream()
                        .sorted(Comparator.comparing(reason -> reason.getReasonType().ordinal()))
                        .map(this::toSavedReason)
                        .toList();

        return ShoeAnalysisResponseDTO.RecommendationSummaryContext.builder()
                .userId(userId)
                .shoeId(shoe.getId())
                .brandName(shoe.getBrandName())
                .shoeName(shoe.getShoeName())
                .fitScore(recommendation.getFitScore())
                .pointSummary(recommendation.getPointSummary())
                .analyzedAt(recommendation.getAnalyzedAt())
                .reasons(reasons)
                .build();
    }

    private MeasurementSession getOwnedCompletedSession(Long userId, Long measurementSessionId) {
        MeasurementSession session = measurementSessionRepository.findById(measurementSessionId)
                .orElseThrow(() -> new MeasurementHandler(ErrorStatus.MEASUREMENT_NOT_FOUND));
        if (!session.getUser().getId().equals(userId)) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_FORBIDDEN);
        }
        if (session.getStatus() != MeasurementStatus.COMPLETED) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_NOT_COMPLETED);
        }
        return session;
    }

    private ShoeAnalysisResponseDTO.ShoeAnalysisItem toShoeAnalysisItem(
            Shoe shoe,
            List<ShoeReview> sourceReviews,
            List<ShoeLabMeasurement> sourceLabs,
            Map<Long, List<ShoeLabMetric>> metricsByMeasurement) {
        List<ShoeAnalysisResponseDTO.ReviewItem> reviews = sourceReviews.stream()
                .map(this::toReview)
                .toList();
        List<ShoeAnalysisResponseDTO.LabMeasurementItem> labs = sourceLabs.stream()
                .map(lab -> toLabMeasurement(
                        lab, metricsByMeasurement.getOrDefault(lab.getId(), List.of())))
                .toList();
        return ShoeAnalysisResponseDTO.ShoeAnalysisItem.builder()
                .shoeId(shoe.getId())
                .brandName(shoe.getBrandName())
                .shoeName(shoe.getShoeName())
                .modelCode(shoe.getModelCode())
                .musinsaUrl(shoe.getMusinsaUrl())
                .price(shoe.getPrice())
                .imageUrl(shoe.getImageUrl())
                .overallRating(shoe.getOverallRating())
                .reviewCount(shoe.getReviewCount())
                .reviews(reviews)
                .labMeasurements(labs)
                .build();
    }

    private ShoeAnalysisResponseDTO.ReviewItem toReview(ShoeReview review) {
        return ShoeAnalysisResponseDTO.ReviewItem.builder()
                .reviewId(review.getId())
                .rating(review.getRating())
                .reviewText(review.getReviewText())
                .source(review.getSource())
                .collectedAt(review.getCollectedAt())
                .build();
    }

    private ShoeAnalysisResponseDTO.LabMeasurementItem toLabMeasurement(
            ShoeLabMeasurement lab, List<ShoeLabMetric> sourceMetrics) {
        List<ShoeAnalysisResponseDTO.RawMetricItem> rawMetrics = sourceMetrics.stream()
                .map(this::toRawMetric)
                .toList();
        return ShoeAnalysisResponseDTO.LabMeasurementItem.builder()
                .measurementId(lab.getId())
                .source(lab.getSource())
                .sourceUrl(lab.getSourceUrl())
                .sourceBrandName(lab.getSourceBrandName())
                .sourceShoeName(lab.getSourceShoeName())
                .sourceModelCode(lab.getSourceModelCode())
                .testedSize(lab.getTestedSize())
                .capturedAt(lab.getCapturedAt())
                .parserVersion(lab.getParserVersion())
                .internalLengthMm(lab.getInternalLengthMm())
                .widthMm(lab.getWidthMm())
                .toeboxWidthMm(lab.getToeboxWidthMm())
                .toeboxHeightMm(lab.getToeboxHeightMm())
                .insoleThicknessMm(lab.getInsoleThicknessMm())
                .heelStackMm(lab.getHeelStackMm())
                .forefootStackMm(lab.getForefootStackMm())
                .rawMetrics(rawMetrics)
                .build();
    }

    private ShoeAnalysisResponseDTO.RawMetricItem toRawMetric(ShoeLabMetric metric) {
        return ShoeAnalysisResponseDTO.RawMetricItem.builder()
                .metricId(metric.getId())
                .canonicalCharacteristic(metric.getCanonicalCharacteristic())
                .sourceMetricName(metric.getSourceMetricName())
                .value(metric.getValue())
                .averageValue(metric.getAverageValue())
                .sourceMinValue(metric.getSourceMinValue())
                .sourceMaxValue(metric.getSourceMaxValue())
                .unit(metric.getUnit())
                .testedSize(metric.getTestedSize())
                .methodName(metric.getMethodName())
                .methodVersion(metric.getMethodVersion())
                .location(metric.getLocation())
                .variant(metric.getVariant())
                .comparisonSampleCount(metric.getComparisonSampleCount())
                .comparisonCohort(metric.getComparisonCohort())
                .rawValueText(metric.getRawValueText())
                .build();
    }

    private ShoeAnalysisResponseDTO.DailyFootAnalysisItem toDaily(DailyFootAnalysis value) {
        if (value == null) return null;
        return ShoeAnalysisResponseDTO.DailyFootAnalysisItem.builder()
                .balanceScore(value.getBalanceScore())
                .leftPressurePercent(value.getLeftPressurePercent())
                .rightPressurePercent(value.getRightPressurePercent())
                .measuredLeftFootSizeMm(value.getMeasuredLeftFootSizeMm())
                .measuredRightFootSizeMm(value.getMeasuredRightFootSizeMm())
                .leftFootWidthMm(value.getLeftFootWidthMm())
                .rightFootWidthMm(value.getRightFootWidthMm())
                .avgTemperatureCelsius(value.getAvgTemperatureCelsius())
                .avgHumidityPercent(value.getAvgHumidityPercent())
                .typeText(value.getTypeText())
                .build();
    }

    private ShoeAnalysisResponseDTO.TinaPedisAnalysisItem toTinea(TinaPedisAnalysis value) {
        if (value == null) return null;
        return ShoeAnalysisResponseDTO.TinaPedisAnalysisItem.builder()
                .fungalSuspicionSafetyScore(value.getFungalSuspicionSafetyScore())
                .skinReactionSafetyScore(value.getSkinReactionSafetyScore())
                .build();
    }

    private ShoeAnalysisResponseDTO.HalluxValgusAnalysisItem toHallux(HalluxValgusAnalysis value) {
        if (value == null) return null;
        return ShoeAnalysisResponseDTO.HalluxValgusAnalysisItem.builder()
                .leftToeAngleDegree(value.getLeftToeAngleDegree())
                .rightToeAngleDegree(value.getRightToeAngleDegree())
                .riskScore(value.getRiskScore())
                .build();
    }

    private ShoeAnalysisResponseDTO.StaticPressureAnalysisItem toStaticPressure(StaticPressureAnalysis value) {
        return ShoeAnalysisResponseDTO.StaticPressureAnalysisItem.builder()
                .analysisId(value.getId())
                .footSide(value.getFootSide())
                .leftPressureRatio(value.getLeftPressureRatio())
                .rightPressureRatio(value.getRightPressureRatio())
                .forefootPressureRatio(value.getForefootPressureRatio())
                .rearfootPressureRatio(value.getRearfootPressureRatio())
                .centerOfPressureX(value.getCenterOfPressureX())
                .centerOfPressureY(value.getCenterOfPressureY())
                .balanceScore(value.getBalanceScore())
                .balanceStatus(value.getBalanceStatus())
                .analysisText(value.getAnalysisText())
                .build();
    }

    private ShoeAnalysisResponseDTO.PressureSensorReadingItem toPressureReading(PressureSensorReading value) {
        return ShoeAnalysisResponseDTO.PressureSensorReadingItem.builder()
                .readingId(value.getId())
                .footSide(value.getFootSide())
                .footRegion(value.getFootRegion())
                .sensorIndex(value.getSensorIndex())
                .pressureValue(value.getPressureValue())
                .pressureUnit(value.getPressureUnit())
                .recordedAt(value.getRecordedAt())
                .build();
    }

    private ShoeAnalysisResponseDTO.SavedReasonItem toSavedReason(ShoeRecommendationReason reason) {
        List<ShoeAnalysisResponseDTO.SavedReviewItem> reviews = reason.getReasonReviews().stream()
                .sorted(Comparator.comparing(rr -> rr.getReview().getId()))
                .map(rr -> ShoeAnalysisResponseDTO.SavedReviewItem.builder()
                        .reviewId(rr.getReview().getId())
                        .reviewText(rr.getReview().getReviewText())
                        .source(rr.getReview().getSource())
                        .build())
                .toList();
        return ShoeAnalysisResponseDTO.SavedReasonItem.builder()
                .reasonType(reason.getReasonType())
                .title(reason.getTitle())
                .riskLevel(reason.getRiskLevel())
                .reviewSummary(reason.getReviewSummary())
                .reviews(reviews)
                .build();
    }
}
