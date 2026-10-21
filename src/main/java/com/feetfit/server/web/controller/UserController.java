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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    @DeleteMapping("/withdraw")
    @Operation(summary = "회원 탈퇴 [은서]", description = """
            로그인한 회원과 개인정보·측정·분석·리포트·추천·개인 이력을 DB에서 실제 삭제합니다.
            Authorization 헤더에 FeetFit Bearer accessToken이 필요하며 탈퇴할 userId는 입력받지 않습니다.
            공통 기기·신발·리뷰·약 상품·관리 콘텐츠는 삭제하지 않습니다.
            진행 중인 측정은 종료하고 탈퇴 후 기존 토큰 및 늦게 도착한 AI 결과의 접근을 차단합니다.
            소유한 S3 이미지와 소셜 연결 해제는 암호화된 정리 작업으로 접수하여 커밋 후 실행·재시도합니다.
            요청 본문은 선택 사항입니다. 카카오는 kakaoAccessToken(카카오 토큰)을 전달하거나 서버의 KAKAO_ADMIN_KEY를 설정합니다.
            애플은 탈퇴 직전 재인증으로 받은 appleAuthorizationCode를 전달합니다.
            소셜 자격 증명이 없어도 서비스 계정은 삭제하지만 socialUnlinkStatus=MANUAL_UNLINK_REQUIRED가 반환됩니다.
            이 경우 클라이언트는 카카오/애플 계정 설정에서 서비스 연결을 직접 해제하도록 안내해야 합니다.
            cleanupPending=true는 외부 정리 작업이 아직 남았다는 뜻이며 서비스 회원 DB 삭제는 이미 완료된 상태입니다.
            소셜 연결 해제가 진행 중이면 재가입은 USER4005(409)로 거절합니다. 완료 후 새 계정으로 가입할 수 있습니다.
            성공 후 클라이언트는 서비스 토큰·캐시를 삭제하고 측정 WebSocket을 종료한 뒤 로그인 화면으로 이동합니다.
            측정 WebSocket은 STOMP CONNECT의 Authorization 헤더에 Bearer 토큰을 전달해야 하며 본인 topic만 구독할 수 있습니다.
            """)
    public ApiResponse<UserResponseDTO.WithdrawUserResultDTO> withdraw(
            @RequestBody(required = false) @Valid UserRequestDTO.WithdrawUserRequestDTO request) {
        return ApiResponse.onSuccess(userService.withdraw(findLoginUser.getCurrentUserId(), request));
    }

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

    @PostMapping("/profile/setup")
    @Operation(
            summary = "기본 정보 최초 등록 [은서]",
            description = """
                    로그인한 사용자의 기본 정보를 최초 등록하고 약관 동의 이력을 저장합니다.
                    Authorization 헤더에 Bearer accessToken이 필요합니다.
                    footSize는 mm 단위로 입력합니다.
                    termsAgreed는 true만 허용합니다.
                    이미 기본 정보가 등록된 유저는 이 API를 사용할 수 없습니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "기본 정보 최초 등록 및 약관 동의 저장 성공",
                    content = @Content(examples = @ExampleObject(value = USER_PROFILE_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "필수값 누락, 범위 오류, 약관 미동의, enum 값 오류, 잘못된 JSON",
                    content = @Content(examples = {
                            @ExampleObject(name = "유효성 검사 실패", value = PROFILE_SETUP_VALIDATION_ERROR_RESPONSE),
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
                    responseCode = "409",
                    description = "이미 기본 정보가 등록된 유저",
                    content = @Content(examples = @ExampleObject(value = PROFILE_ALREADY_SETUP_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<UserResponseDTO.UserProfileResponseDTO> setupProfile(
            @RequestBody @Valid UserRequestDTO.UserProfileSetupRequestDTO request
    ) {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(userService.setupProfile(userId, request));
    }

    @PatchMapping("/profile")
    @Operation(
            summary = "기본 정보 수정 [은서]",
            description = """
                    로그인한 사용자의 기본 정보를 수정합니다.
                    Authorization 헤더에 Bearer accessToken이 필요합니다.
                    약관 동의는 처리하지 않습니다.
                    footSize는 mm 단위로 입력합니다.
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
                "footSize": 240,
                "gender": "FEMALE",
                "requiresProfileSetup": false
              }
            }
            """;

    private static final String PROFILE_SETUP_VALIDATION_ERROR_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON400",
              "message": "잘못된 요청입니다.",
              "result": {
                "age": "나이는 1세 이상이어야 합니다.",
                "footSize": "발사이즈는 150mm 이상이어야 합니다.",
                "termsAgreed": "약관 동의는 필수입니다."
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
                "weightKg": "몸무게는 1kg 이상이어야 합니다.",
                "footSize": "발사이즈는 150mm 이상이어야 합니다."
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

    private static final String PROFILE_ALREADY_SETUP_RESPONSE = """
            {
              "isSuccess": false,
              "code": "USER4004",
              "message": "이미 기본 정보가 등록된 사용자입니다.",
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
