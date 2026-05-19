package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.ApiResponse;
import com.feetfit.server.service.OAuthService.KakaoLoginCommandService;
import com.feetfit.server.web.dto.kakao.KakaoLoginRequestDTO;
import com.feetfit.server.web.dto.user.UserResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final KakaoLoginCommandService kakaoLoginCommandService;

    @PostMapping("/kakao")
    @Operation(
            summary = "카카오 로그인 [은서]",
            description = """
                    카카오 Access Token으로 로그인합니다.
                    - 기존 유저가 있으면 바로 서비스 토큰을 발급합니다.
                    - 기존 유저가 없으면 카카오 socialId 기준으로 신규 유저를 생성한 뒤 서비스 토큰을 발급합니다.
                    - 기본 정보가 아직 비어 있으면 requiresProfileSetup=true를 반환합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "카카오 로그인 성공",
                    content = @Content(examples = @ExampleObject(value = KAKAO_LOGIN_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청값 누락, 잘못된 JSON, 유효하지 않은 카카오 Access Token",
                    content = @Content(examples = {
                            @ExampleObject(name = "accessToken 누락", value = VALIDATION_ERROR_RESPONSE),
                            @ExampleObject(name = "카카오 로그인 실패", value = KAKAO_LOGIN_FAILED_RESPONSE)
                    })
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<UserResponseDTO.UserLoginResponseDTO> kakaoLogin(
            @RequestBody @Valid KakaoLoginRequestDTO request
    ) {
        UserResponseDTO.UserLoginResponseDTO serviceToken = kakaoLoginCommandService.login(request.getAccessToken());
        return ApiResponse.onSuccess(serviceToken);
    }

    private static final String KAKAO_LOGIN_SUCCESS_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
                "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
                "grantType": "Bearer",
                "expiresIn": 1779287307449,
                "requiresProfileSetup": true
              }
            }
            """;

    private static final String VALIDATION_ERROR_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON400",
              "message": "잘못된 요청입니다.",
              "result": {
                "accessToken": "카카오 Access Token은 필수입니다."
              }
            }
            """;

    private static final String KAKAO_LOGIN_FAILED_RESPONSE = """
            {
              "isSuccess": false,
              "code": "AUTH4003",
              "message": "카카오 로그인에 실패했습니다.",
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
