package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.ApiResponse;
import com.feetfit.server.service.DrugService.DrugQueryService;
import com.feetfit.server.service.DrugService.DrugRecommendationQueryService;
import com.feetfit.server.jwt.FindLoginUser;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@Validated
@Tag(name = "Drug", description = "약 추천 및 약 정보 제공 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/drugs")
public class DrugController {

    private final DrugQueryService drugQueryService;
    private final DrugRecommendationQueryService drugRecommendationQueryService;
    private final FindLoginUser findLoginUser;

    @GetMapping("/recommendations")
    @Operation(summary = "내 발 상태 기반 약·발 관리 제품 추천 조회",
            description = "최신 완료 측정 리포트에 저장된 우선순위와 요약을 반환합니다. 약 추천은 측정 완료 소켓 전송 후 비동기로 생성됩니다. summaryStatus가 NOT_READY, PENDING, GENERATING이면 준비 중이며 COMPLETED 또는 FAILED가 될 때까지 다시 조회할 수 있습니다. 우선순위는 제품군 정렬용이며 임상 위험도가 아닙니다. 조회 중 GPT·식약처 호출은 하지 않습니다.")
    public ApiResponse<DrugResponseDTO.DrugRecommendationResultDTO> getRecommendations(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.onSuccess(drugRecommendationQueryService.get(findLoginUser.getCurrentUserId(), category, q, page, size));
    }

    @PostMapping("/recommendations/refresh")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "내 최신 리포트의 약 추천 생성·실패 재시도", description = "이미 생성된 동일 근거의 GPT 문구는 재생성하지 않습니다.")
    public ApiResponse<Void> refreshRecommendations() {
        drugRecommendationQueryService.refresh(findLoginUser.getCurrentUserId());
        return ApiResponse.onSuccess(null);
    }

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
                    - 인증 토큰을 전달하면 최신 완료 측정에 저장된 개인별 summary와 summaryStatus, summarySource를 함께 반환합니다.
                    - summary는 현재 선택한 카테고리만이 아니라 사용자 발 상태 전체를 기준으로 생성된 공통 추천 문구입니다.
                    - 비로그인·완료 측정 없음·추천 준비 전에는 summary=null입니다. NOT_READY, PENDING, GENERATING은 준비 중 상태입니다.
                    - 목록은 DB에 저장된 데이터를 조회하며 요청 중 식약처 API를 호출하지 않습니다.
                    """
    )
    public ApiResponse<DrugResponseDTO.DrugListResultDTO> getDrugList(
            @RequestParam(defaultValue = "athletes-foot") String category,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size는 1 이상이어야 합니다.")
            @Max(value = 100, message = "size는 100 이하이어야 합니다.") int size
    ) {
        var result = drugQueryService.getDrugList(category, q, page, size);
        Long userId = findLoginUser.getCurrentUserId();
        if (userId != null) {
            result = drugRecommendationQueryService.withRecommendationSummary(userId, result);
        }
        return ApiResponse.onSuccess(result);
    }

    @GetMapping("/permit")
    @Operation(
            summary = "약 허가 및 회수 정보 조회 [은서]",
            description = """
                    식약처 품목기준코드(itemSeq)로 의약품 허가정보와 회수/판매중지 정보를 조회합니다.
                    - 약 리스트 응답의 id가 itemSeq입니다.
                    - 온라인 구매 가능 생활용품은 식약처 품목기준코드가 아니므로 이 API 대상이 아닙니다.
                    - 효능·사용법·허가·회수 정보는 DB에 저장된 최신 수집 결과를 반환합니다.
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
