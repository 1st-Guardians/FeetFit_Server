package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.ExceptionAdvice;
import com.feetfit.server.apiPayload.exception.handler.AuthHandler;
import com.feetfit.server.jwt.TokenProvider;
import com.feetfit.server.service.OAuthService.KakaoLoginCommandService;
import com.feetfit.server.web.dto.user.UserResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ExceptionAdvice.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KakaoLoginCommandService kakaoLoginCommandService;

    @MockBean
    private TokenProvider tokenProvider;

    @MockBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;

    @Test
    void kakaoLogin_success_returnsServiceTokensAndProfileSetupFlag() throws Exception {
        given(kakaoLoginCommandService.login("kakao-access-token"))
                .willReturn(UserResponseDTO.UserLoginResponseDTO.builder()
                        .accessToken("service-access-token")
                        .refreshToken("service-refresh-token")
                        .grantType("Bearer")
                        .expiresIn(1779287307449L)
                        .requiresProfileSetup(true)
                        .build());

        mockMvc.perform(post("/api/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accessToken": "kakao-access-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.message").value("성공입니다."))
                .andExpect(jsonPath("$.result.accessToken").value("service-access-token"))
                .andExpect(jsonPath("$.result.refreshToken").value("service-refresh-token"))
                .andExpect(jsonPath("$.result.grantType").value("Bearer"))
                .andExpect(jsonPath("$.result.expiresIn").value(1779287307449L))
                .andExpect(jsonPath("$.result.requiresProfileSetup").value(true));
    }

    @Test
    void kakaoLogin_blankAccessToken_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accessToken": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."))
                .andExpect(jsonPath("$.result.accessToken").value("카카오 Access Token은 필수입니다."));
    }

    @Test
    void kakaoLogin_serviceThrowsAuthHandler_returnsKakaoLoginFailedError() throws Exception {
        given(kakaoLoginCommandService.login("invalid-token"))
                .willThrow(new AuthHandler(ErrorStatus.KAKAO_LOGIN_FAILED));

        mockMvc.perform(post("/api/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accessToken": "invalid-token"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("AUTH4003"))
                .andExpect(jsonPath("$.message").value("카카오 로그인에 실패했습니다."));
    }
}
