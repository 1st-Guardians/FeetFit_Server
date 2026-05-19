package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.ApiResponse;
import com.feetfit.server.jwt.FindLoginUser;
import com.feetfit.server.service.UserService.UserService;
import com.feetfit.server.web.dto.user.UserRequestDTO;
import com.feetfit.server.web.dto.user.UserResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "사용자 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final FindLoginUser findLoginUser;

    @GetMapping("/profile")
    @Operation(
            summary = "유저 정보 조회 [은서]",
            description = """
                    로그인한 사용자의 기본 정보를 조회합니다.
                    Authorization 헤더에 Bearer accessToken이 필요합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "유저 정보 조회 성공",
                    content = @Content(examples = @ExampleObject(value = USER_PROFILE_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "접근 권한 없음",
                    content = @Content(examples = @ExampleObject(value = FORBIDDEN_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "토큰의 userId에 해당하는 유저가 존재하지 않음",
                    content = @Content(examples = @ExampleObject(value = USER_NOT_FOUND_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<UserResponseDTO.UserProfileResponseDTO> getProfile() {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(userService.getProfile(userId));
    }

    @PatchMapping("/profile")
    @Operation(
            summary = "기본 정보 입력 [은서]",
            description = """
                    로그인한 사용자의 기본 정보를 입력하거나 수정합니다.
                    Authorization 헤더에 Bearer accessToken이 필요합니다.
                    gender는 MALE 또는 FEMALE만 허용합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "기본 정보 입력 또는 수정 성공",
                    content = @Content(examples = @ExampleObject(value = USER_PROFILE_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "필수값 누락, 범위 오류, enum 값 오류, 잘못된 JSON",
                    content = @Content(examples = {
                            @ExampleObject(name = "유효성 검사 실패", value = PROFILE_VALIDATION_ERROR_RESPONSE),
                            @ExampleObject(name = "enum 값 오류", value = INVALID_JSON_OR_ENUM_RESPONSE)
                    })
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "접근 권한 없음",
                    content = @Content(examples = @ExampleObject(value = FORBIDDEN_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "토큰의 userId에 해당하는 유저가 존재하지 않음",
                    content = @Content(examples = @ExampleObject(value = USER_NOT_FOUND_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<UserResponseDTO.UserProfileResponseDTO> updateProfile(
            @RequestBody @Valid UserRequestDTO.UserProfileUpdateRequestDTO request
    ) {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(userService.updateProfile(userId, request));
    }

    private static final String USER_PROFILE_SUCCESS_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "userId": 1,
                "nickname": "은서",
                "age": 24,
                "heightCm": 165.5,
                "weightKg": 52.3,
                "gender": "FEMALE",
                "profileImageUrl": "https://example.com/profile.png",
                "requiresProfileSetup": false
              }
            }
            """;

    private static final String PROFILE_VALIDATION_ERROR_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON400",
              "message": "잘못된 요청입니다.",
              "result": {
                "age": "나이는 1세 이상이어야 합니다.",
                "heightCm": "키는 30cm 이상이어야 합니다.",
                "weightKg": "몸무게는 1kg 이상이어야 합니다."
              }
            }
            """;

    private static final String INVALID_JSON_OR_ENUM_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON400",
              "message": "요청 본문 형식이 잘못되었습니다. enum 값은 허용된 대문자 값으로 입력해야 합니다.",
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

    private static final String FORBIDDEN_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON403",
              "message": "금지된 요청입니다.",
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

    private static final String INTERNAL_SERVER_ERROR_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON500",
              "message": "서버 에러, 관리자에게 문의 바랍니다.",
              "result": null
            }
            """;
}
