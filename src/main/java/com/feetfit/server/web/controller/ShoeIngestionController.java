package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.ApiResponse;
import com.feetfit.server.domain.enums.ShoeImportMatchStatus;
import com.feetfit.server.domain.enums.ShoeImportSource;
import com.feetfit.server.service.ShoeService.ShoeIngestionService;
import com.feetfit.server.web.dto.shoe.ShoeIngestionRequestDTO;
import com.feetfit.server.web.dto.shoe.ShoeIngestionResponseDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/shoes")
public class ShoeIngestionController {

    private final ShoeIngestionService shoeIngestionService;

    @PostMapping("/imports/musinsa")
    public ApiResponse<ShoeIngestionResponseDTO.ImportResult> importMusinsa(
            @RequestBody @Valid ShoeIngestionRequestDTO.MusinsaImportRequest request) {
        return ApiResponse.onSuccess(shoeIngestionService.importMusinsa(request));
    }

    @PostMapping("/imports/runrepeat")
    public ApiResponse<ShoeIngestionResponseDTO.ImportResult> importRunRepeat(
            @RequestBody @Valid ShoeIngestionRequestDTO.RunRepeatImportRequest request) {
        return ApiResponse.onSuccess(shoeIngestionService.importRunRepeat(request));
    }

    @GetMapping("/import-audits")
    public ApiResponse<ShoeIngestionResponseDTO.AuditPageResult> getImportAudits(
            @RequestParam(required = false) ShoeImportSource source,
            @RequestParam(required = false) ShoeImportMatchStatus matchStatus,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return ApiResponse.onSuccess(
                shoeIngestionService.getImportAudits(source, matchStatus, page, size));
    }
}
