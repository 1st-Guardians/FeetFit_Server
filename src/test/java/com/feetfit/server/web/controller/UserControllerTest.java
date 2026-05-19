package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.ExceptionAdvice;
import com.feetfit.server.apiPayload.exception.handler.UserHandler;
import com.feetfit.server.jwt.FindLoginUser;
import com.feetfit.server.jwt.TokenProvider;
import com.feetfit.server.service.UserService.UserService;
import com.feetfit.server.web.dto.user.UserResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ExceptionAdvice.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private FindLoginUser findLoginUser;

    @MockBean
    private TokenProvider tokenProvider;

    @MockBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;

    @Test
    void getProfile_success_returnsCurrentUserProfile() throws Exception {
        given(findLoginUser.getCurrentUserId()).willReturn(1L);
        given(userService.getProfile(1L)).willReturn(completeProfileResponse());

        mockMvc.perform(get("/api/users/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.result.userId").value(1L))
                .andExpect(jsonPath("$.result.nickname").value("은서"))
                .andExpect(jsonPath("$.result.age").value(24))
                .andExpect(jsonPath("$.result.heightCm").value(165.5))
                .andExpect(jsonPath("$.result.weightKg").value(52.3))
                .andExpect(jsonPath("$.result.gender").value("FEMALE"))
                .andExpect(jsonPath("$.result.profileImageUrl").value("https://example.com/profile.png"))
                .andExpect(jsonPath("$.result.requiresProfileSetup").value(false));
    }

    @Test
    void getProfile_userNotFound_returnsNotFoundError() throws Exception {
        given(findLoginUser.getCurrentUserId()).willReturn(999L);
        given(userService.getProfile(999L)).willThrow(new UserHandler(ErrorStatus.USER_NOT_FOUND));

        mockMvc.perform(get("/api/users/profile"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("USER4001"))
                .andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다."));
    }

    @Test
    void updateProfile_success_returnsUpdatedProfile() throws Exception {
        given(findLoginUser.getCurrentUserId()).willReturn(1L);
        given(userService.updateProfile(eq(1L), any())).willReturn(completeProfileResponse());

        mockMvc.perform(patch("/api/users/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProfileRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.result.requiresProfileSetup").value(false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidProfileRequests")
    void updateProfile_invalidRequest_returnsValidationError(
            String name,
            String requestBody,
            String field,
            String expectedMessage
    ) throws Exception {
        mockMvc.perform(patch("/api/users/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.message").value("잘못된 요청입니다."))
                .andExpect(jsonPath("$.result." + field).value(expectedMessage));
    }

    @Test
    void updateProfile_lowercaseGender_returnsInvalidBodyError() throws Exception {
        mockMvc.perform(patch("/api/users/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "은서",
                                  "age": 24,
                                  "heightCm": 165.5,
                                  "weightKg": 52.3,
                                  "gender": "female"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.message").value("요청 본문 형식이 잘못되었습니다. enum 값은 허용된 대문자 값으로 입력해야 합니다."));
    }

    private static Stream<Arguments> invalidProfileRequests() {
        String longNickname = "a".repeat(51);
        String longProfileImageUrl = "https://example.com/" + "a".repeat(260);

        return Stream.of(
                Arguments.of(
                        "nickname 누락",
                        """
                                {
                                  "age": 24,
                                  "heightCm": 165.5,
                                  "weightKg": 52.3,
                                  "gender": "FEMALE"
                                }
                                """,
                        "nickname",
                        "닉네임은 필수입니다."
                ),
                Arguments.of(
                        "nickname 공백",
                        """
                                {
                                  "nickname": "",
                                  "age": 24,
                                  "heightCm": 165.5,
                                  "weightKg": 52.3,
                                  "gender": "FEMALE"
                                }
                                """,
                        "nickname",
                        "닉네임은 필수입니다."
                ),
                Arguments.of(
                        "nickname 길이 초과",
                        """
                                {
                                  "nickname": "%s",
                                  "age": 24,
                                  "heightCm": 165.5,
                                  "weightKg": 52.3,
                                  "gender": "FEMALE"
                                }
                                """.formatted(longNickname),
                        "nickname",
                        "닉네임은 50자 이하여야 합니다."
                ),
                Arguments.of(
                        "age 누락",
                        """
                                {
                                  "nickname": "은서",
                                  "heightCm": 165.5,
                                  "weightKg": 52.3,
                                  "gender": "FEMALE"
                                }
                                """,
                        "age",
                        "나이는 필수입니다."
                ),
                Arguments.of(
                        "age 최소값 미만",
                        """
                                {
                                  "nickname": "은서",
                                  "age": 0,
                                  "heightCm": 165.5,
                                  "weightKg": 52.3,
                                  "gender": "FEMALE"
                                }
                                """,
                        "age",
                        "나이는 1세 이상이어야 합니다."
                ),
                Arguments.of(
                        "age 최대값 초과",
                        """
                                {
                                  "nickname": "은서",
                                  "age": 121,
                                  "heightCm": 165.5,
                                  "weightKg": 52.3,
                                  "gender": "FEMALE"
                                }
                                """,
                        "age",
                        "나이는 120세 이하여야 합니다."
                ),
                Arguments.of(
                        "heightCm 누락",
                        """
                                {
                                  "nickname": "은서",
                                  "age": 24,
                                  "weightKg": 52.3,
                                  "gender": "FEMALE"
                                }
                                """,
                        "heightCm",
                        "키는 필수입니다."
                ),
                Arguments.of(
                        "heightCm 최소값 미만",
                        """
                                {
                                  "nickname": "은서",
                                  "age": 24,
                                  "heightCm": 29.9,
                                  "weightKg": 52.3,
                                  "gender": "FEMALE"
                                }
                                """,
                        "heightCm",
                        "키는 30cm 이상이어야 합니다."
                ),
                Arguments.of(
                        "heightCm 최대값 초과",
                        """
                                {
                                  "nickname": "은서",
                                  "age": 24,
                                  "heightCm": 250.1,
                                  "weightKg": 52.3,
                                  "gender": "FEMALE"
                                }
                                """,
                        "heightCm",
                        "키는 250cm 이하여야 합니다."
                ),
                Arguments.of(
                        "weightKg 누락",
                        """
                                {
                                  "nickname": "은서",
                                  "age": 24,
                                  "heightCm": 165.5,
                                  "gender": "FEMALE"
                                }
                                """,
                        "weightKg",
                        "몸무게는 필수입니다."
                ),
                Arguments.of(
                        "weightKg 최소값 미만",
                        """
                                {
                                  "nickname": "은서",
                                  "age": 24,
                                  "heightCm": 165.5,
                                  "weightKg": 0.9,
                                  "gender": "FEMALE"
                                }
                                """,
                        "weightKg",
                        "몸무게는 1kg 이상이어야 합니다."
                ),
                Arguments.of(
                        "weightKg 최대값 초과",
                        """
                                {
                                  "nickname": "은서",
                                  "age": 24,
                                  "heightCm": 165.5,
                                  "weightKg": 300.1,
                                  "gender": "FEMALE"
                                }
                                """,
                        "weightKg",
                        "몸무게는 300kg 이하여야 합니다."
                ),
                Arguments.of(
                        "gender 누락",
                        """
                                {
                                  "nickname": "은서",
                                  "age": 24,
                                  "heightCm": 165.5,
                                  "weightKg": 52.3
                                }
                                """,
                        "gender",
                        "성별은 필수입니다."
                ),
                Arguments.of(
                        "profileImageUrl 길이 초과",
                        """
                                {
                                  "nickname": "은서",
                                  "age": 24,
                                  "heightCm": 165.5,
                                  "weightKg": 52.3,
                                  "gender": "FEMALE",
                                  "profileImageUrl": "%s"
                                }
                                """.formatted(longProfileImageUrl),
                        "profileImageUrl",
                        "프로필 이미지 URL은 255자 이하여야 합니다."
                )
        );
    }

    private static String validProfileRequest() {
        return """
                {
                  "nickname": "은서",
                  "age": 24,
                  "heightCm": 165.5,
                  "weightKg": 52.3,
                  "gender": "FEMALE",
                  "profileImageUrl": "https://example.com/profile.png"
                }
                """;
    }

    private static UserResponseDTO.UserProfileResponseDTO completeProfileResponse() {
        return UserResponseDTO.UserProfileResponseDTO.builder()
                .userId(1L)
                .nickname("은서")
                .age(24)
                .heightCm(165.5F)
                .weightKg(52.3F)
                .gender("FEMALE")
                .profileImageUrl("https://example.com/profile.png")
                .requiresProfileSetup(false)
                .build();
    }
}
