package com.feetfit.server.service.ShoeService;

import com.feetfit.server.domain.enums.ReasonType;
import com.feetfit.server.web.dto.shoe.ShoeResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Coordinates the optional slow summary generation outside database transactions.
 * Each query/command dependency is a separate Spring proxy call, so the sequence is
 * short read transaction -> external AI call -> save transaction -> fresh read transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShoeDetailService {

    private static final String LEGACY_QUANTITATIVE_REVIEW_MARKER = "정량 분석 기준";
    private static final String LEGACY_REVIEW_COUNT_MARKER = "관련 착화 리뷰";
    private static final Pattern LEGACY_MEASUREMENT_LEVEL_SENTENCE = Pattern.compile(
            "^(?:RunRepeat 비교 특성에서 )?"
                    + "(?:발볼 공간|앞코 공간|쿠션감|뒤꿈치 구조 강성|통기성|충격 완화 수준|반발력)"
                    + "은 (?:낮은 편|보통 수준|높은 편)(?:입니다)?$");
    private static final Pattern LEGACY_WEIGHT_LEVEL_SENTENCE = Pattern.compile(
            "^착화 시 (?:가볍게|다소 무게감이) 느껴지는 편(?:입니다)?$");
    private static final Pattern SENTENCE_DELIMITER = Pattern.compile("[.!?。]+");

    private final ShoeSearchQueryService shoeSearchQueryService;
    private final ShoeSummaryGenerationTrigger shoeSummaryGenerationTrigger;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ShoeResponseDTO.ShoeDetailResultDTO getShoeDetail(
            Long userId, Long shoeId, Long requestedMeasurementSessionId) {
        ShoeResponseDTO.ShoeDetailResultDTO detail = shoeSearchQueryService.getShoeDetail(
                userId, shoeId, requestedMeasurementSessionId);

        if (!canGenerate(userId, detail) || hasCompleteSummary(detail)) {
            return detail;
        }

        Long resolvedMeasurementSessionId = detail.getMeasurementSessionId();
        try {
            if (!shoeSummaryGenerationTrigger.generateNow(
                    userId, resolvedMeasurementSessionId, shoeId)) {
                return detail;
            }

            // Always pin the second read to the first response's resolved session. This avoids
            // mixing summaries if another measurement becomes current during Ollama generation.
            ShoeResponseDTO.ShoeDetailResultDTO refreshed = shoeSearchQueryService.getShoeDetail(
                    userId, shoeId, resolvedMeasurementSessionId);
            if (hasCompleteSummary(refreshed)) {
                return refreshed;
            }
            log.warn(
                    "Saved shoe summary was incomplete after fresh read. "
                            + "measurementSessionId={}, shoeId={}",
                    resolvedMeasurementSessionId, shoeId);
        } catch (RuntimeException exception) {
            // Summary generation enhances an otherwise valid detail response. AI/network/save
            // failures therefore fall back to the first DB snapshot instead of turning it into 5xx.
            log.warn(
                    "Synchronous shoe summary generation failed unexpectedly. "
                            + "measurementSessionId={}, shoeId={}",
                    resolvedMeasurementSessionId, shoeId, exception);
        }
        return detail;
    }

    private boolean canGenerate(
            Long userId, ShoeResponseDTO.ShoeDetailResultDTO detail) {
        return userId != null
                && detail.getMeasurementSessionId() != null
                && detail.getFitScore() != null;
    }

    private boolean hasCompleteSummary(ShoeResponseDTO.ShoeDetailResultDTO detail) {
        List<ShoeResponseDTO.ReasonResultDTO> reasons = detail.getReasons();
        if (!StringUtils.hasText(detail.getPointSummary())
                || isOnlyLegacyMeasurementLevels(detail.getPointSummary())
                || reasons == null
                || reasons.size() != 3) {
            return false;
        }
        EnumSet<ReasonType> reasonTypes = EnumSet.noneOf(ReasonType.class);
        return reasons.stream().allMatch(reason ->
                reason != null
                        && reason.getReasonType() != null
                        && reasonTypes.add(reason.getReasonType())
                        && StringUtils.hasText(reason.getReviewSummary())
                        && !isLegacyReviewSummary(reason.getReviewSummary()))
                && reasonTypes.equals(EnumSet.allOf(ReasonType.class));
    }

    private boolean isLegacyReviewSummary(String summary) {
        return summary.contains(LEGACY_QUANTITATIVE_REVIEW_MARKER)
                || summary.contains(LEGACY_REVIEW_COUNT_MARKER);
    }

    private boolean isOnlyLegacyMeasurementLevels(String summary) {
        List<String> sentences = SENTENCE_DELIMITER
                .splitAsStream(summary)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        if (sentences.size() < 2) {
            return false;
        }
        boolean includesMeasurementLevel = sentences.stream()
                .anyMatch(sentence -> LEGACY_MEASUREMENT_LEVEL_SENTENCE.matcher(sentence).matches());
        return includesMeasurementLevel && sentences.stream().allMatch(sentence ->
                LEGACY_MEASUREMENT_LEVEL_SENTENCE.matcher(sentence).matches()
                        || LEGACY_WEIGHT_LEVEL_SENTENCE.matcher(sentence).matches());
    }
}
