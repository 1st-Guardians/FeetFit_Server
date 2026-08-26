package com.feetfit.server.service.ShoeService;

import com.feetfit.server.web.dto.shoe.ShoeRecommendationRunResponseDTO;

public interface ShoeRecommendationRunService {
    ShoeRecommendationRunResponseDTO.RunResultDTO prepareRun(
            Long userId, Long measurementSessionId, int expectedCount);

    ShoeRecommendationRunResponseDTO.RunResultDTO startRun(
            Long userId, Long measurementSessionId, int expectedCount, boolean restartCompleted);

    ShoeRecommendationRunResponseDTO.RunResultDTO completeRun(
            Long userId, Long measurementSessionId);

    ShoeRecommendationRunResponseDTO.RunResultDTO failRun(
            Long userId, Long measurementSessionId, String failureDetail);

    ShoeRecommendationRunResponseDTO.RunResultDTO getRun(
            Long userId, Long measurementSessionId);
}
