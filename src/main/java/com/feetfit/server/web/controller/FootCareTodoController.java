package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.ApiResponse;
import com.feetfit.server.jwt.FindLoginUser;
import com.feetfit.server.service.FootCareTodoService.FootCareTodoService;
import com.feetfit.server.web.dto.footcare.FootCareTodoRequestDTO;
import com.feetfit.server.web.dto.footcare.FootCareTodoResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "FootCareTodo", description = "발 관리 투두 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/foot-care-todos")
public class FootCareTodoController {

    private final FootCareTodoService footCareTodoService;
    private final FindLoginUser findLoginUser;

    @GetMapping
    @Operation(
            summary = "발 관리 투두 조회 [은서]",
            description = "로그인한 사용자의 오늘 날짜 발 관리 투두 목록을 todoDate 오름차순으로 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "발 관리 투두 조회 성공. 오늘 투두가 없어도 빈 배열로 성공 응답합니다.",
                    content = @Content(examples = {
                            @ExampleObject(name = "투두 목록 있음", value = FOOT_CARE_TODO_LIST_SUCCESS_RESPONSE),
                            @ExampleObject(name = "투두 목록 없음", value = FOOT_CARE_TODO_LIST_EMPTY_RESPONSE)
                    })
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
    public ApiResponse<FootCareTodoResponseDTO.FootCareTodoListResponseDTO> getMyFootCareTodos() {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(footCareTodoService.getMyFootCareTodos(userId));
    }

    @PatchMapping("/{todoId}/completion")
    @Operation(
            summary = "발 관리 투두 완료 여부 체크 [은서]",
            description = "로그인한 사용자의 발 관리 투두 완료 여부를 true 또는 false로 변경합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "발 관리 투두 완료 여부 변경 성공",
                    content = @Content(examples = @ExampleObject(value = FOOT_CARE_TODO_COMPLETION_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "isCompleted 누락, 잘못된 JSON, todoId 형식 오류",
                    content = @Content(examples = {
                            @ExampleObject(name = "유효성 검사 실패", value = COMPLETION_VALIDATION_ERROR_RESPONSE),
                            @ExampleObject(name = "잘못된 JSON", value = INVALID_JSON_RESPONSE),
                            @ExampleObject(name = "todoId 형식 오류", value = INVALID_PATH_VARIABLE_RESPONSE)
                    })
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "유저가 없거나, 내 발 관리 투두가 아님",
                    content = @Content(examples = {
                            @ExampleObject(name = "유저 없음", value = USER_NOT_FOUND_RESPONSE),
                            @ExampleObject(name = "발 관리 투두 없음", value = FOOT_CARE_TODO_NOT_FOUND_RESPONSE)
                    })
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<FootCareTodoResponseDTO.FootCareTodoInfoResponseDTO> updateCompletion(
            @PathVariable Long todoId,
            @RequestBody @Valid FootCareTodoRequestDTO.UpdateCompletionRequestDTO request
    ) {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(footCareTodoService.updateCompletion(userId, todoId, request));
    }

    private static final String FOOT_CARE_TODO_LIST_SUCCESS_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "totalCount": 1,
                "hasTodayTodos": true,
                "message": "오늘 발 관리 투두입니다.",
                "todos": [
                  {
                    "todoId": 1,
                    "title": "수건으로 발 당기기",
                    "healthType": "POSTURE",
                    "youtubeUrl": "https://www.youtube.com/watch?v=stretching",
                    "isCompleted": false,
                    "completedAt": null,
                    "todoDate": "2026-05-20T09:00:00"
                  }
                ]
              }
            }
            """;

    private static final String FOOT_CARE_TODO_LIST_EMPTY_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "totalCount": 0,
                "hasTodayTodos": false,
                "message": "오늘 발 관리 투두가 없습니다.",
                "todos": []
              }
            }
            """;

    private static final String FOOT_CARE_TODO_COMPLETION_SUCCESS_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "todoId": 1,
                "title": "수건으로 발 당기기",
                "healthType": "POSTURE",
                "youtubeUrl": "https://www.youtube.com/watch?v=stretching",
                "isCompleted": true,
                "completedAt": "2026-05-20T09:10:00",
                "todoDate": "2026-05-20T09:00:00"
              }
            }
            """;

    private static final String COMPLETION_VALIDATION_ERROR_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON400",
              "message": "잘못된 요청입니다.",
              "result": {
                "isCompleted": "완료 여부는 필수입니다."
              }
            }
            """;

    private static final String INVALID_JSON_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON400",
              "message": "요청 본문 형식이 잘못되었습니다. enum 값은 허용된 대문자 값으로 입력해야 합니다.",
              "result": null
            }
            """;

    private static final String INVALID_PATH_VARIABLE_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON400",
              "message": "요청 경로 변수 형식이 잘못되었습니다.",
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

    private static final String FOOT_CARE_TODO_NOT_FOUND_RESPONSE = """
            {
              "isSuccess": false,
              "code": "FOOT_CARE_TODO4001",
              "message": "발 관리 투두를 찾을 수 없습니다.",
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
