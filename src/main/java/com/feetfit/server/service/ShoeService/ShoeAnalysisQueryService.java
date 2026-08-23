package com.feetfit.server.service.ShoeService;

import com.feetfit.server.web.dto.shoe.ShoeAnalysisResponseDTO;

public interface ShoeAnalysisQueryService {
    ShoeAnalysisResponseDTO.RecommendationContext getRecommendationContext(
            Long userId, Long measurementSessionId, int page, int size);

    ShoeAnalysisResponseDTO.RecommendationSummaryContext getRecommendationSummaryContext(
            Long userId, Long shoeId);
}
