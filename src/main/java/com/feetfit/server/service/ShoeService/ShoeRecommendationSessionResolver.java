package com.feetfit.server.service.ShoeService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.MeasurementHandler;
import com.feetfit.server.apiPayload.exception.handler.ShoeHandler;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.ShoeRecommendationRun;
import com.feetfit.server.domain.enums.ShoeRecommendationRunStatus;
import com.feetfit.server.repository.MeasurementSessionRepository;
import com.feetfit.server.repository.ShoeRecommendationRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShoeRecommendationSessionResolver {

    private final ShoeRecommendationRunRepository runRepository;
    private final MeasurementSessionRepository measurementSessionRepository;

    public Optional<ResolvedRecommendationSession> resolveCurrentCompleted(Long userId) {
        return runRepository.findLatestByUserIdAndStatus(
                        userId, ShoeRecommendationRunStatus.COMPLETED, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(this::toScope);
    }

    public ResolvedRecommendationSession requireCurrentCompleted(Long userId) {
        return resolveCurrentCompleted(userId)
                .orElseThrow(() -> new ShoeHandler(ErrorStatus.SHOE_FIT_SCORE_UNAVAILABLE));
    }

    public ResolvedRecommendationSession requireCompleted(Long userId, Long measurementSessionId) {
        MeasurementSession session = measurementSessionRepository.findById(measurementSessionId)
                .orElseThrow(() -> new MeasurementHandler(ErrorStatus.MEASUREMENT_NOT_FOUND));
        if (!session.getUser().getId().equals(userId)) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_FORBIDDEN);
        }
        ShoeRecommendationRun run = runRepository.findByMeasurementSessionId(measurementSessionId)
                .orElseThrow(() -> new ShoeHandler(ErrorStatus.SHOE_RECOMMENDATION_RUN_NOT_FOUND));
        if (run.getStatus() != ShoeRecommendationRunStatus.COMPLETED) {
            throw new ShoeHandler(ErrorStatus.SHOE_RECOMMENDATION_RUN_NOT_COMPLETED);
        }
        return toScope(run);
    }

    public ResolvedRecommendationSession resolveCompleted(
            Long userId, Long requestedMeasurementSessionId) {
        return requestedMeasurementSessionId == null
                ? requireCurrentCompleted(userId)
                : requireCompleted(userId, requestedMeasurementSessionId);
    }

    private ResolvedRecommendationSession toScope(ShoeRecommendationRun run) {
        return new ResolvedRecommendationSession(
                run.getId(),
                run.getMeasurementSession().getId(),
                run.getMeasurementSession().getUser().getId());
    }

    public record ResolvedRecommendationSession(
            Long recommendationRunId,
            Long measurementSessionId,
            Long userId) {
    }
}
