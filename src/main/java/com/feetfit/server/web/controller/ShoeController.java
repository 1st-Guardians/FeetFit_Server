package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.ApiResponse;
import com.feetfit.server.domain.enums.ShoeSort;
import com.feetfit.server.jwt.FindLoginUser;
import com.feetfit.server.service.ShoeService.ShoeAiClient;
import com.feetfit.server.service.ShoeService.ShoeCommandService;
import com.feetfit.server.service.ShoeService.ShoeQueryService;
import com.feetfit.server.service.ShoeService.ShoeSearchQueryService;
import com.feetfit.server.web.dto.shoe.ShoeRequestDTO;
import com.feetfit.server.web.dto.shoe.ShoeResponseDTO;
import com.feetfit.server.web.dto.shoe.ShoeSearchResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@Tag(name = "Shoe", description = "신발 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shoes")
public class ShoeController {

    private final ShoeQueryService shoeQueryService;
    private final ShoeCommandService shoeCommandService;
    private final ShoeSearchQueryService shoeSearchQueryService;
    private final ShoeAiClient shoeAiClient;
    private final FindLoginUser findLoginUser;

    @GetMapping
    @Operation(
            summary = "신발 리스트 조회 [민지]",
            description = """
                    신발 리스트를 조회합니다.
                    Authorization 헤더에 Bearer accessToken이 필요합니다.
                    - sort: FIT_SCORE(발 적합도순), RATING(별점순), CLICK(관심도순)
                    - 발 적합도순이 디폴트값입니다.
                    - 측정을 하지 않은 유저가 FIT_SCORE 요청 시 400을 반환합니다.
                    - page: 0부터 시작 (기본값 0)
                    - size: 페이지당 신발 수 (기본값 20)
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "신발 리스트 조회 성공",
                    content = @Content(examples = @ExampleObject(value = SHOE_LIST_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "측정 이력 없는 유저의 발 적합도순 요청",
                    content = @Content(examples = @ExampleObject(value = FIT_SCORE_UNAVAILABLE_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<ShoeResponseDTO.ShoeListResultDTO> getShoeList(
            @RequestParam(defaultValue = "FIT_SCORE") ShoeSort sort,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size는 1 이상이어야 합니다.") @Max(value = 100, message = "size는 100 이하이어야 합니다.") int size
    ) {
        Long userId = findLoginUser.getCurrentUserId();
        ShoeSort resolvedSort = (sort != null) ? sort : ShoeSort.FIT_SCORE;  // null 방어
        ShoeRequestDTO.ShoeListRequestDTO request = new ShoeRequestDTO.ShoeListRequestDTO(resolvedSort, page, size);
        return ApiResponse.onSuccess(shoeQueryService.getShoeList(userId, request));
    }

    @GetMapping("/{shoeId}")
    @Operation(
            summary = "신발 디테일 조회 [민지]",
            description = """
                    신발 상세 정보를 조회합니다.
                    Authorization 헤더에 Bearer accessToken이 필요합니다.
                    - 로그인한 유저의 fitScore를 함께 반환합니다.
                    - 측정 이력이 없는 유저는 fitScore, pointSummary(착용 포인트)가 null로 반환됩니다.
                    - 부위별(발볼/뒤꿈치/깔창) AI가 선택한 리뷰 3개씩 반환합니다.
                    - 추천 데이터가 없는 유저는 reasons(발볼, 뒤꿈치, 깔창에 대한 내용)가 빈 배열로 반환됩니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "신발 디테일 조회 성공",
                    content = @Content(examples = @ExampleObject(value = SHOE_DETAIL_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "신발을 찾을 수 없음",
                    content = @Content(examples = @ExampleObject(value = SHOE_NOT_FOUND_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<ShoeResponseDTO.ShoeDetailResultDTO> getShoeDetail(
            @PathVariable Long shoeId,
            HttpServletRequest httpRequest
    ) {
        Long userId = findLoginUser.getCurrentUserId();
        ShoeResponseDTO.ShoeDetailResultDTO result = shoeSearchQueryService.getShoeDetail(userId, shoeId);

        if (result.getPointSummary() == null) {
            String authHeader = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
            shoeAiClient.requestShoeSummaryGeneration(shoeId, userId, authHeader);
        }

        return ApiResponse.onSuccess(result);
    }

    @PostMapping("/{shoeId}/click")
    @Operation(
            summary = "신발 클릭 수 증가 [민지]",
            description = """
                신발 클릭 수를 1 증가시킵니다.
                Authorization 헤더에 Bearer accessToken이 필요합니다.
                - 유저가 신발을 클릭했을 때 호출해주세요.
                """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "신발 클릭 수 증가 성공",
                    content = @Content(examples = @ExampleObject(value = SHOE_CLICK_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "신발을 찾을 수 없음",
                    content = @Content(examples = @ExampleObject(value = SHOE_NOT_FOUND_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<ShoeResponseDTO.ShoeClickResultDTO> clickShoe(
            @PathVariable Long shoeId
    ) {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(shoeCommandService.clickShoe(userId, shoeId));
    }

    @PostMapping("/recommendations")
    @Operation(
            summary = "사용자-신발 발 적합도 배치 생성/갱신",
            description = """
                Feetfit_AI(AI 서버)가 측정 결과와 신발 리뷰 데이터를 분석한 뒤,
                신발 전체에 대한 발 적합도를 배치로 저장/갱신할 때 호출합니다.
                Authorization 헤더에 Bearer accessToken이 필요합니다 (무좀/무지외반 분석 저장과 동일하게,
                Feetfit_AI가 사용자의 accessToken을 그대로 포워딩합니다).
                - 토큰에서 추출한 userId 기준으로 (userId, shoeId) 조합의 ShoeRecommendation을 upsert합니다.
                - reasons 배열에 FOREFOOT/HEEL/INSOLE을 한 번에 담아 보낼 수 있습니다.
                - reasons는 최소 1개 이상이어야 하며, 비어 있으면 400 에러가 발생합니다.
                - 기존에 저장된 부위별(FOREFOOT/HEEL/INSOLE) 근거는 이번 요청의 reasons로 통째로 교체되므로,
                  3개를 모두 유지하고 싶다면 매번 3개를 모두 함께 보내야 합니다.
                - DB에 없는 shoeId는 건너뛰고 skippedShoeIds로 반환합니다.
                - reviewIds는 반드시 해당 shoeId의 리뷰여야 합니다. 존재하지 않거나 다른 신발의 리뷰 id가
                  섞여 있으면 그 신발만 건너뛰고 invalidReviewShoeIds로 반환합니다 (배치 전체는 계속 처리됩니다).
                """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "발 적합도 배치 저장 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 형식 오류"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "사용자를 찾을 수 없음",
                    content = @Content(examples = @ExampleObject(value = USER_NOT_FOUND_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<ShoeResponseDTO.SaveShoeRecommendationResultDTO> saveShoeRecommendations(
            @RequestBody @Valid ShoeRequestDTO.SaveShoeRecommendationDTO request
    ) {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(shoeCommandService.saveShoeRecommendations(userId, request));
    }

    @PostMapping("/{shoeId}/summaries")
    @Operation(
            summary = "신발 착용 포인트·부위별 요약 저장",
            description = """
                Feetfit_AI가 신발 상세 조회 요청을 받아 pointSummary와 각 부위별 reviewSummary를 생성한 뒤 호출합니다.
                Authorization 헤더에 Bearer accessToken이 필요합니다 (사용자의 accessToken을 그대로 포워딩).
                - 토큰에서 추출한 userId와 shoeId 조합의 ShoeRecommendation이 없으면 404를 반환합니다.
                - 존재하지 않는 reasonType은 무시합니다 (reasons 배열에 없는 타입은 기존 값 유지).
                """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "요약 저장 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 형식 오류"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "해당 사용자-신발 적합도 데이터가 없음",
                    content = @Content(examples = @ExampleObject(value = SHOE_NOT_FOUND_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<Void> saveShoeSummaries(
            @PathVariable Long shoeId,
            @RequestBody @Valid ShoeRequestDTO.SaveShoeSummariesDTO request
    ) {
        Long userId = findLoginUser.getCurrentUserId();
        shoeCommandService.saveShoeSummaries(userId, shoeId, request);
        return ApiResponse.onSuccess(null);
    }

    @GetMapping("/search")
    @Operation(
            summary = "신발 검색 [민지]",
            description = """
                키워드로 신발을 검색합니다.
                Authorization 헤더에 Bearer accessToken이 필요합니다.
                - 신발명, 브랜드명 기준으로 검색합니다.
                - 검색 시 자동으로 검색 기록이 저장됩니다.
                - 만료된 기록(1주일 초과) 자동 삭제됩니다.
                - 검색 기록이 10개 초과 시 가장 오래된 기록 자동 삭제됩니다.
                """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "신발 검색 성공",
                    content = @Content(examples = @ExampleObject(value = SHOE_SEARCH_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<ShoeSearchResponseDTO.ShoeSearchResultDTO> search(
            @RequestParam @NotBlank(message = "keyword는 비어 있을 수 없습니다.") String keyword,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size는 1 이상이어야 합니다.") @Max(value = 100, message = "size는 100 이하이어야 합니다.") int size
    ) {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(shoeSearchQueryService.search(userId, keyword, page, size));
    }

    @GetMapping("/search/suggestions")
    @Operation(
            summary = "연관 검색어 조회 [민지]",
            description = """
                    키워드로 신발을 검색하되, 검색 기록을 저장하지 않습니다.
                    Authorization 헤더에 Bearer accessToken이 필요합니다.
                    - 신발명, 브랜드명 기준으로 전체 DB에서 검색합니다.
                    - 자동완성/연관 검색어 목적으로 사용하며, 검색 기록에 남지 않습니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "연관 검색어 조회 성공",
                    content = @Content(examples = @ExampleObject(value = SHOE_SEARCH_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<ShoeSearchResponseDTO.ShoeSearchResultDTO> searchSuggestions(
            @RequestParam @NotBlank(message = "keyword는 비어 있을 수 없습니다.") String keyword,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size는 1 이상이어야 합니다.") @Max(value = 100, message = "size는 100 이하이어야 합니다.") int size
    ) {
        findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(shoeSearchQueryService.searchSuggestions(keyword, page, size));
    }

    @GetMapping("/search/history")
    @Operation(
            summary = "최근 검색 기록 조회 [민지]",
            description = """
                최근 일주일 이내의 검색 기록을 조회합니다.
                Authorization 헤더에 Bearer accessToken이 필요합니다.
                - 최대 10개까지 반환합니다.
                - 최신순으로 정렬됩니다.
                """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "검색 기록 조회 성공",
                    content = @Content(examples = @ExampleObject(value = SHOE_SEARCH_HISTORY_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<ShoeSearchResponseDTO.ShoeSearchHistoryResultDTO> getSearchHistory() {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(shoeSearchQueryService.getSearchHistory(userId));
    }

    @DeleteMapping("/search/history/{historyId}")
    @Operation(
            summary = "검색 기록 삭제 [민지]",
            description = """
                검색 기록을 삭제합니다.
                Authorization 헤더에 Bearer accessToken이 필요합니다.
                - 본인의 검색 기록만 삭제할 수 있습니다.
                """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "검색 기록 삭제 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "검색 기록을 찾을 수 없음",
                    content = @Content(examples = @ExampleObject(value = SHOE_SEARCH_HISTORY_NOT_FOUND_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<Void> deleteSearchHistory(@PathVariable Long historyId) {
        Long userId = findLoginUser.getCurrentUserId();
        shoeSearchQueryService.deleteSearchHistory(userId, historyId);
        return ApiResponse.onSuccess(null);
    }

    @GetMapping("/recommendations/top3")
    @Operation(
            summary = "추천 신발 Top3 조회 [민지]",
            description = """
                사용자와 적합도가 가장 높은 신발 3종을 조회합니다.
                Authorization 헤더에 Bearer accessToken이 필요합니다.
                - 측정 이력이 없는 유저는 400을 반환합니다.
                """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "추천 신발 Top3 조회 성공",
                    content = @Content(examples = @ExampleObject(value = SHOE_TOP3_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "측정 이력 없음",
                    content = @Content(examples = @ExampleObject(value = FIT_SCORE_UNAVAILABLE_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<ShoeResponseDTO.ShoeRecommendTop3ResultDTO> getTop3ShoesByFitScore() {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(shoeQueryService.getTop3ShoesByFitScore(userId));
    }

    private static final String SHOE_DETAIL_SUCCESS_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "id": 1,
                "brandName": "푸마",
                "shoeName": "푸마 x 로제 스피드캣 PRM 블랙 원 화이트",
                "shoeUrl": "https://www.puma.com/kr",
                "price": 159000,
                "imageUrl": "https://example.com/puma-speedcat.jpg",
                "overallRating": 4.3,
                "clickCount": 0,
                "reviewCount": 0,
                "fitScore": 87.0,
                "pointSummary": "발볼이 슬림하게 나온 편이라 발볼 넓으면 반 사이즈 업 추천, 정사이즈는 전체적으로 딱 맞는 핏이다. 로우하고 날렵한 디자인이라 청바지, 슬랙스, 스커트까지 깔끔하게 잘 어울리는 데일리용 스니커즈다. 디자인 위주 신발이라 쿠션감은 크지 않지만 가볍고 예쁘게 신기 좋다.",
                "reasons": [
                  {
                    "reasonType": "INSOLE",
                    "title": "깔창 얇음",
                    "riskLevel": "HIGH",
                    "reviewSummary": "이 제품은 깔창이 얇아 충격 흡수를 충분히 제공하지 못할 수 있습니다.",
                    "reviewTexts": [
                      "깔창이 얇아서 오래 걸으면 발바닥이 조금 피로했어요.",
                      "쿠션감이 크지 않고 바닥이 얇은 편입니다.",
                      "디자인은 예쁘지만 푹신한 느낌은 적었어요."
                    ]
                  },
                  {
                    "reasonType": "HEEL",
                    "title": "뒤꿈치 까짐 주의",
                    "riskLevel": "MEDIUM",
                    "reviewSummary": "사용자의 보행 패턴에서 뒤꿈치 압력 집중이 일부 확인됩니다.",
                    "reviewTexts": [
                      "처음 신었을 때 뒤꿈치가 조금 까졌어요.",
                      "뒷부분이 단단해서 오래 신으면 마찰이 느껴졌습니다.",
                      "양말을 두껍게 신지 않으면 뒤꿈치가 불편했어요."
                    ]
                  },
                  {
                    "reasonType": "FOREFOOT",
                    "title": "발볼 넓음",
                    "riskLevel": "LOW",
                    "reviewSummary": "사용자의 발 구조 분석 결과, 발볼이 큰 편에 해당합니다.",
                    "reviewTexts": [
                      "발볼이 여유 있게 나와서 편하게 신을 수 있었어요.",
                      "평소 발볼이 넓은 편인데도 답답하지 않았습니다.",
                      "발볼이 좁은 사람은 조금 헐겁게 느낄 수도 있어요."
                    ]
                  }
                ]
              }
            }
            """;

    private static final String SHOE_TOP3_SUCCESS_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "shoes": [
                  {
                    "id": 18,
                    "brandName": "호카",
                    "shoeName": "본다이 8",
                    "shoeUrl": "https://www.hoka.com/kr/bondi-8",
                    "price": 199000,
                    "imageUrl": "https://example.com/hoka-bondi-8.jpg",
                    "overallRating": 4.7,
                    "fitScore": 94.2
                  },
                  {
                    "id": 10,
                    "brandName": "뉴발란스",
                    "shoeName": "1080v13",
                    "shoeUrl": "https://www.newbalance.co.kr/product/1080v13",
                    "price": 229000,
                    "imageUrl": "https://example.com/nb-1080v13.jpg",
                    "overallRating": 4.6,
                    "fitScore": 93.0
                  },
                  {
                    "id": 1,
                    "brandName": "나이키",
                    "shoeName": "에어 줌 페가수스 41",
                    "shoeUrl": "https://www.nike.com/kr/t/air-zoom-pegasus-41",
                    "price": 139000,
                    "imageUrl": "https://example.com/nike-pegasus-41.jpg",
                    "overallRating": 4.5,
                    "fitScore": 92.5
                  }
                ]
              }
            }
            """;

    private static final String SHOE_SEARCH_SUCCESS_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "results": [
                  {
                    "id": 1,
                    "brandName": "나이키",
                    "shoeName": "에어 줌 페가수스 41",
                    "price": 139000,
                    "imageUrl": "https://example.com/nike-pegasus-41.jpg",
                    "overallRating": 4.5
                  },
                  {
                    "id": 2,
                    "brandName": "나이키",
                    "shoeName": "에어 맥스 270",
                    "price": 169000,
                    "imageUrl": "https://example.com/nike-air-max-270.jpg",
                    "overallRating": 4.3
                  }
                ],
                "currentPage": 0,
                "totalPages": 1,
                "totalElements": 4,
                "hasNext": false
              }
            }
            """;

    private static final String SHOE_SEARCH_HISTORY_SUCCESS_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "histories": [
                  {
                    "id": 1,
                    "keyword": "나이키"
                  },
                  {
                    "id": 2,
                    "keyword": "프론트 화이팅 백엔드도 화이팅"
                  }
                ]
              }
            }
            """;

    private static final String SHOE_CLICK_SUCCESS_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "id": 1,
                "clickCount": 1
              }
            }
            """;

    private static final String SHOE_NOT_FOUND_RESPONSE = """
            {
              "isSuccess": false,
              "code": "SHOE4001",
              "message": "신발 정보를 찾을 수 없습니다.",
              "result": null
            }
            """;

    private static final String USER_NOT_FOUND_RESPONSE = """
            {
              "isSuccess": false,
              "code": "USER4001",
              "message": "사용자를 찾을 수 없습니다.",
              "result": null
            }
            """;

    private static final String SHOE_SEARCH_HISTORY_NOT_FOUND_RESPONSE = """
            {
              "isSuccess": false,
              "code": "SHOE4003",
              "message": "검색 기록을 찾을 수 없습니다.",
              "result": null
            }
            """;

    private static final String SHOE_LIST_SUCCESS_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "shoes": [
                  {
                    "id": 1,
                    "brandName": "호카",
                    "shoeName": "본다이 8",
                    "shoeUrl": "https://www.hoka.com/kr/bondi-8",
                    "price": 199000,
                    "imageUrl": "https://example.com/hoka-bondi-8.jpg",
                    "overallRating": 4.7,
                    "clickCount": 0,
                    "reviewCount": 0,
                    "fitScore": 92.5
                  },
                  {
                    "id": 2,
                    "brandName": "뉴발란스",
                    "shoeName": "990v6",
                    "shoeUrl": "https://www.newbalance.co.kr/product/990v6",
                    "price": 259000,
                    "imageUrl": "https://example.com/nb-990v6.jpg",
                    "overallRating": 4.7,
                    "clickCount": 0,
                    "reviewCount": 0,
                    "fitScore": 88.3
                  }
                ],
                "currentPage": 0,
                "totalPages": 1,
                "totalElements": 20,
                "hasNext": false
              }
            }
            """;

    private static final String FIT_SCORE_UNAVAILABLE_RESPONSE = """
            {
              "isSuccess": false,
              "code": "SHOE4002",
              "message": "측정 이력이 없어 발 적합도순 정렬을 사용할 수 없습니다.",
              "result": null
            }
            """;

    private static final String UNAUTHORIZED_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON401",
              "message": "인증이 필요합니다.",
              "result": null
            }
            """;

    private static final String INTERNAL_SERVER_ERROR_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON500",
              "message": "서버 에러, 관리자에게 문의 바랍니다.",
              "result": null
            }
            """;
}