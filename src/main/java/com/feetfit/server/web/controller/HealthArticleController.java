package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.ApiResponse;
import com.feetfit.server.jwt.FindLoginUser;
import com.feetfit.server.service.HealthArticleService.HealthArticleService;
import com.feetfit.server.web.dto.health.HealthArticleResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "HealthArticle", description = "건강 아티클 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/articles")
public class HealthArticleController {

    private final HealthArticleService healthArticleService;
    private final FindLoginUser findLoginUser;

    @GetMapping
    @Operation(
            summary = "헬스 아티클 조회 [은서]",
            description = "로그인한 사용자에게 연결된 헬스 아티클 목록을 발행일 내림차순으로 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "헬스 아티클 조회 성공",
                    content = @Content(examples = @ExampleObject(value = HEALTH_ARTICLE_LIST_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "토큰의 userId에 해당하는 유저가 없음",
                    content = @Content(examples = @ExampleObject(value = USER_NOT_FOUND_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<HealthArticleResponseDTO.HealthArticleListResponseDTO> getMyHealthArticles() {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(healthArticleService.getMyHealthArticles(userId));
    }

    private static final String HEALTH_ARTICLE_LIST_SUCCESS_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "totalCount": 1,
                "articles": [
                  {
                    "articleId": 1,
                    "title": "무지외반증 예방을 위한 발 관리",
                    "url": "https://example.com/articles/hallux-valgus-care",
                    "publisher": "FeetFit",
                    "publishedAt": "2026-05-20T09:00:00",
                    "healthType": "HALLUX_VALGUS",
                    "description": "무지외반증 예방을 위한 생활 습관과 스트레칭 정보를 제공합니다."
                  }
                ]
              }
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
