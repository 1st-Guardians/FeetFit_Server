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

/**
 * Coordinates the optional slow summary generation outside database transactions.
 * Each query/command dependency is a separate Spring proxy call, so the sequence is
 * short read transaction -> external AI call -> save transaction -> fresh read transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShoeDetailService {

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
                || reasons == null
                || reasons.size() != 3) {
            return false;
        }
        EnumSet<ReasonType> reasonTypes = EnumSet.noneOf(ReasonType.class);
        return reasons.stream().allMatch(reason ->
                reason != null
                        && reason.getReasonType() != null
                        && reasonTypes.add(reason.getReasonType())
                        && StringUtils.hasText(reason.getReviewSummary()))
                && reasonTypes.equals(EnumSet.allOf(ReasonType.class));
    }
}
