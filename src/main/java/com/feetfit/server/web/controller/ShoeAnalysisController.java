package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.ApiResponse;
import com.feetfit.server.jwt.FindLoginUser;
import com.feetfit.server.service.ShoeService.ShoeAnalysisQueryService;
import com.feetfit.server.web.dto.shoe.ShoeAnalysisResponseDTO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/shoe-analysis")
public class ShoeAnalysisController {

    private final ShoeAnalysisQueryService shoeAnalysisQueryService;
    private final FindLoginUser findLoginUser;

    @GetMapping("/recommendation-context")
    public ApiResponse<ShoeAnalysisResponseDTO.RecommendationContext> getRecommendationContext(
            @RequestParam @Min(1) Long measurementSessionId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int size) {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(shoeAnalysisQueryService.getRecommendationContext(
                userId, measurementSessionId, page, size));
    }

    @GetMapping("/shoes/{shoeId}/recommendation-summary-context")
    public ApiResponse<ShoeAnalysisResponseDTO.RecommendationSummaryContext> getRecommendationSummaryContext(
            @PathVariable @Min(1) Long shoeId,
            @RequestParam @Min(1) Long measurementSessionId) {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(
                shoeAnalysisQueryService.getRecommendationSummaryContext(
                        userId, measurementSessionId, shoeId));
    }
}
