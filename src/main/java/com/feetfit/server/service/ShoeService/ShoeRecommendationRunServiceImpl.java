package com.feetfit.server.service.ShoeService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.MeasurementHandler;
import com.feetfit.server.apiPayload.exception.handler.ShoeHandler;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.ShoeRecommendationRun;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.ShoeRecommendationRunStatus;
import com.feetfit.server.repository.MeasurementSessionRepository;
import com.feetfit.server.repository.ShoeRecommendationRepository;
import com.feetfit.server.repository.ShoeRecommendationReasonRepository;
import com.feetfit.server.repository.ShoeRecommendationRunRepository;
import com.feetfit.server.web.dto.shoe.ShoeRecommendationRunResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ShoeRecommendationRunServiceImpl implements ShoeRecommendationRunService {

    private final MeasurementSessionRepository measurementSessionRepository;
    private final ShoeRecommendationRunRepository runRepository;
    private final ShoeRecommendationRepository recommendationRepository;
    private final ShoeRecommendationReasonRepository reasonRepository;

    @Override
    @Transactional
    public boolean claimAutomaticRun(Long userId, Long measurementSessionId, int expectedCount) {
        requirePositiveExpectedCount(expectedCount);
        MeasurementSession session = lockOwnedCompletedSession(userId, measurementSessionId);
        ShoeRecommendationRun run = runRepository.findByMeasurementSessionIdForUpdate(measurementSessionId)
                .orElseGet(() -> runRepository.save(ShoeRecommendationRun.builder()
                        .measurementSession(session)
                        .expectedCount(expectedCount)
                        .build()));
        if (run.getStatus() == ShoeRecommendationRunStatus.RUNNING
                || run.getStatus() == ShoeRecommendationRunStatus.COMPLETED) {
            return false;
        }
        try {
            run.start(expectedCount, false, LocalDateTime.now());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw runConflict(ex.getMessage());
        }
        return true;
    }

    @Override
    @Transactional
    public boolean claimAutomaticRetry(
            Long userId,
            Long measurementSessionId,
            int expectedCount) {
        requirePositiveExpectedCount(expectedCount);
        MeasurementSession session = lockOwnedCompletedSession(userId, measurementSessionId);
        ShoeRecommendationRun run = runRepository.findByMeasurementSessionIdForUpdate(measurementSessionId)
                .orElseGet(() -> runRepository.save(ShoeRecommendationRun.builder()
                        .measurementSession(session)
                        .expectedCount(expectedCount)
                        .build()));
        if (run.getStatus() == ShoeRecommendationRunStatus.RUNNING
                || run.getStatus() == ShoeRecommendationRunStatus.COMPLETED) {
            return false;
        }
        try {
            run.start(expectedCount, false, LocalDateTime.now());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw runConflict(ex.getMessage());
        }
        return true;
    }

    @Override
    @Transactional
    public ShoeRecommendationRunResponseDTO.RunResultDTO prepareRun(
            Long userId, Long measurementSessionId, int expectedCount) {
        requirePositiveExpectedCount(expectedCount);
        MeasurementSession session = lockOwnedCompletedSession(userId, measurementSessionId);
        ShoeRecommendationRun run = runRepository.findByMeasurementSessionIdForUpdate(measurementSessionId)
                .orElseGet(() -> runRepository.save(ShoeRecommendationRun.builder()
                        .measurementSession(session)
                        .expectedCount(expectedCount)
                        .build()));
        if (!run.getExpectedCount().equals(expectedCount)) {
            throw runConflict("기존 추천 실행의 expectedCount와 일치하지 않습니다.");
        }
        return ShoeRecommendationRunResponseDTO.RunResultDTO.from(run);
    }

    @Override
    @Transactional
    public ShoeRecommendationRunResponseDTO.RunResultDTO startRun(
            Long userId, Long measurementSessionId, int expectedCount, boolean restartCompleted) {
        requirePositiveExpectedCount(expectedCount);
        MeasurementSession session = lockOwnedCompletedSession(userId, measurementSessionId);
        ShoeRecommendationRun run = runRepository.findByMeasurementSessionIdForUpdate(measurementSessionId)
                .orElseGet(() -> runRepository.save(ShoeRecommendationRun.builder()
                        .measurementSession(session)
                        .expectedCount(expectedCount)
                        .build()));
        try {
            run.start(expectedCount, restartCompleted, LocalDateTime.now());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw runConflict(ex.getMessage());
        }
        return ShoeRecommendationRunResponseDTO.RunResultDTO.from(run);
    }

    @Override
    @Transactional
    public ShoeRecommendationRunResponseDTO.RunResultDTO completeRun(
            Long userId, Long measurementSessionId) {
        lockOwnedCompletedSession(userId, measurementSessionId);
        ShoeRecommendationRun run = getLockedRun(measurementSessionId);
        long persistedCount = recommendationRepository.countByMeasurementSessionId(measurementSessionId);
        long incompleteReasonSetCount = reasonRepository
                .countIncompleteReasonSetsByMeasurementSessionId(measurementSessionId);
        if (incompleteReasonSetCount > 0) {
            throw runConflict("FOREFOOT/HEEL/INSOLE reason 3개가 완전하지 않은 추천이 있습니다.");
        }
        try {
            run.updateProcessedCount(persistedCount);
            run.complete(LocalDateTime.now());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw runConflict(ex.getMessage());
        }
        return ShoeRecommendationRunResponseDTO.RunResultDTO.from(run);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ShoeRecommendationRunResponseDTO.RunResultDTO failRun(
            Long userId, Long measurementSessionId, String failureDetail) {
        lockOwnedCompletedSession(userId, measurementSessionId);
        ShoeRecommendationRun run = getLockedRun(measurementSessionId);
        try {
            run.fail(failureDetail);
        } catch (IllegalStateException ex) {
            throw runConflict(ex.getMessage());
        }
        return ShoeRecommendationRunResponseDTO.RunResultDTO.from(run);
    }

    @Override
    @Transactional(readOnly = true)
    public ShoeRecommendationRunResponseDTO.RunResultDTO getRun(
            Long userId, Long measurementSessionId) {
        ShoeRecommendationRun run = runRepository
                .findByMeasurementSessionIdAndMeasurementSessionUserId(measurementSessionId, userId)
                .orElseThrow(() -> new ShoeHandler(ErrorStatus.SHOE_RECOMMENDATION_RUN_NOT_FOUND));
        return ShoeRecommendationRunResponseDTO.RunResultDTO.from(run);
    }

    private MeasurementSession lockOwnedCompletedSession(Long userId, Long measurementSessionId) {
        MeasurementSession session = measurementSessionRepository.findByIdForUpdate(measurementSessionId)
                .orElseThrow(() -> new MeasurementHandler(ErrorStatus.MEASUREMENT_NOT_FOUND));
        if (!session.getUser().getId().equals(userId)) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_FORBIDDEN);
        }
        if (session.getStatus() != MeasurementStatus.COMPLETED) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_NOT_COMPLETED);
        }
        return session;
    }

    private ShoeRecommendationRun getLockedRun(Long measurementSessionId) {
        return runRepository.findByMeasurementSessionIdForUpdate(measurementSessionId)
                .orElseThrow(() -> new ShoeHandler(ErrorStatus.SHOE_RECOMMENDATION_RUN_NOT_FOUND));
    }

    private static void requirePositiveExpectedCount(int expectedCount) {
        if (expectedCount <= 0) {
            throw new ShoeHandler(ErrorStatus._BAD_REQUEST, "expectedCount는 양수여야 합니다.");
        }
    }

    private static ShoeHandler runConflict(String detail) {
        return new ShoeHandler(ErrorStatus.SHOE_RECOMMENDATION_RUN_CONFLICT, detail);
    }
}
