package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.ApiResponse;
import com.feetfit.server.service.ShoeService.ShoeCharacteristicQueryService;
import com.feetfit.server.web.dto.shoe.ShoeCharacteristicResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Shoe", description = "신발 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shoes")
public class ShoeCharacteristicController {

    private final ShoeCharacteristicQueryService characteristicQueryService;

    @GetMapping("/{shoeId}/characteristics")
    @Operation(
            summary = "RunRepeat 기반 신발 상품 특성 조회",
            description = "사용자 발 데이터 없이 최신 RunRepeat 실측 snapshot의 객관적 특성을 조회합니다."
    )
    public ApiResponse<ShoeCharacteristicResponseDTO.Result> getCharacteristics(
            @PathVariable Long shoeId) {
        return ApiResponse.onSuccess(characteristicQueryService.getCharacteristics(shoeId));
    }
}
