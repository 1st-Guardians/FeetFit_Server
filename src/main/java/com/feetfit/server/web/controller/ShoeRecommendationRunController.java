package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.ApiResponse;
import com.feetfit.server.jwt.FindLoginUser;
import com.feetfit.server.service.ShoeService.ShoeRecommendationRunService;
import com.feetfit.server.web.dto.shoe.ShoeRecommendationRunRequestDTO;
import com.feetfit.server.web.dto.shoe.ShoeRecommendationRunResponseDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/shoe-analysis/recommendation-runs")
public class ShoeRecommendationRunController {

    private final ShoeRecommendationRunService runService;
    private final FindLoginUser findLoginUser;

    @PostMapping("/{measurementSessionId}/prepare")
    public ApiResponse<ShoeRecommendationRunResponseDTO.RunResultDTO> prepare(
            @PathVariable @Min(1) Long measurementSessionId,
            @RequestBody @Valid ShoeRecommendationRunRequestDTO.PrepareRunDTO request) {
        return ApiResponse.onSuccess(runService.prepareRun(
                findLoginUser.getCurrentUserId(), measurementSessionId, request.getExpectedCount()));
    }

    @PostMapping("/{measurementSessionId}/start")
    public ApiResponse<ShoeRecommendationRunResponseDTO.RunResultDTO> start(
            @PathVariable @Min(1) Long measurementSessionId,
            @RequestBody @Valid ShoeRecommendationRunRequestDTO.StartRunDTO request) {
        return ApiResponse.onSuccess(runService.startRun(
                findLoginUser.getCurrentUserId(), measurementSessionId,
                request.getExpectedCount(), request.isRestartCompleted()));
    }

    @PostMapping("/{measurementSessionId}/complete")
    public ApiResponse<ShoeRecommendationRunResponseDTO.RunResultDTO> complete(
            @PathVariable @Min(1) Long measurementSessionId) {
        return ApiResponse.onSuccess(runService.completeRun(
                findLoginUser.getCurrentUserId(), measurementSessionId));
    }

    @PostMapping("/{measurementSessionId}/fail")
    public ApiResponse<ShoeRecommendationRunResponseDTO.RunResultDTO> fail(
            @PathVariable @Min(1) Long measurementSessionId,
            @RequestBody @Valid ShoeRecommendationRunRequestDTO.FailRunDTO request) {
        return ApiResponse.onSuccess(runService.failRun(
                findLoginUser.getCurrentUserId(), measurementSessionId, request.getFailureDetail()));
    }

    @GetMapping("/{measurementSessionId}")
    public ApiResponse<ShoeRecommendationRunResponseDTO.RunResultDTO> get(
            @PathVariable @Min(1) Long measurementSessionId) {
        return ApiResponse.onSuccess(runService.getRun(
                findLoginUser.getCurrentUserId(), measurementSessionId));
    }
}
