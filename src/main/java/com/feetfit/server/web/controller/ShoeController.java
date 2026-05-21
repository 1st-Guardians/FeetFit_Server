package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.ApiResponse;
import com.feetfit.server.domain.enums.ShoeSort;
import com.feetfit.server.jwt.FindLoginUser;
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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
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

    @GetMapping("/recommendations/top3")
    @Operation(
            summary = "추천 신발 Top3 조회 [민지]",
            description = """
                사용자와 적합도가 가장 높은 신발 3종을 조회합니다.
                Authorization 헤더에 Bearer accessToken이 필요합니다.
                - 측정 이력이 없는 유저는 빈 배열을 반환합니다.
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
                    "price": 199000,
                    "imageUrl": "https://example.com/hoka-bondi-8.jpg",
                    "overallRating": 4.7,
                    "fitScore": 94.2
                  },
                  {
                    "id": 10,
                    "brandName": "뉴발란스",
                    "shoeName": "1080v13",
                    "price": 229000,
                    "imageUrl": "https://example.com/nb-1080v13.jpg",
                    "overallRating": 4.6,
                    "fitScore": 93.0
                  },
                  {
                    "id": 1,
                    "brandName": "나이키",
                    "shoeName": "에어 줌 페가수스 41",
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