package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.ApiResponse;
import com.feetfit.server.service.DrugService.DrugQueryService;
import com.feetfit.server.web.dto.drug.DrugResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@Tag(name = "Drug", description = "약 추천 및 약 정보 제공 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/drugs")
public class DrugController {

    private final DrugQueryService drugQueryService;

    @GetMapping("/categories")
    @Operation(
            summary = "약 추천 카테고리 조회 [은서]",
            description = """
                    약 추천/정보 제공 카테고리를 조회합니다.
                    - online=false: 식약처 의약품 데이터 연동 제품군
                    - online=true: 온라인 구매 가능 생활용품 추천 제품군
                    """
    )
    public ApiResponse<DrugResponseDTO.DrugCategoryResultDTO> getCategories() {
        return ApiResponse.onSuccess(drugQueryService.getCategories());
    }

    @GetMapping
    @Operation(
            summary = "약 추천 및 약 정보 리스트 조회 [은서]",
            description = """
                    카테고리별 약 추천/약 정보를 조회합니다.
                    - category: athletes-foot, bunion-band, wound-care
                    - 기존 wound-ointment, wound-dressing, foot-care 요청은 wound-care로 처리합니다.
                    - q: 제품명 검색어. 최대 50자까지 적용됩니다.
                    - page: 0부터 시작합니다.
                    - size: 페이지당 결과 수입니다.
                    - athletes-foot는 식약처 e약은요 데이터를 전달합니다.
                    - bunion-band는 온라인 구매 가능 추천 제품 데이터를 전달합니다.
                    - wound-care는 상처 치료 연고(식약처)와 보호밴드·드레싱/발가락 보호·발 관리(온라인)를 함께 전달합니다.
                    """
    )
    public ApiResponse<DrugResponseDTO.DrugListResultDTO> getDrugList(
            @RequestParam(defaultValue = "athletes-foot") String category,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size는 1 이상이어야 합니다.")
            @Max(value = 100, message = "size는 100 이하이어야 합니다.") int size
    ) {
        return ApiResponse.onSuccess(drugQueryService.getDrugList(category, q, page, size));
    }

    @GetMapping("/permit")
    @Operation(
            summary = "약 허가 및 회수 정보 조회 [은서]",
            description = """
                    식약처 품목기준코드(itemSeq)로 의약품 허가정보와 회수/판매중지 정보를 조회합니다.
                    - 약 리스트 응답의 id가 itemSeq입니다.
                    - 온라인 구매 가능 생활용품은 식약처 품목기준코드가 아니므로 이 API 대상이 아닙니다.
                    """
    )
    public ApiResponse<DrugResponseDTO.DrugPermitResultDTO> getPermitInfo(
            @RequestParam
            @Pattern(regexp = "^\\d{6,20}$", message = "올바른 품목기준코드가 필요해요.")
            String itemSeq
    ) {
        return ApiResponse.onSuccess(drugQueryService.getPermitInfo(itemSeq));
    }
}
