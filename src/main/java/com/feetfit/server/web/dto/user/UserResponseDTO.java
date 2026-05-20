package com.feetfit.server.web.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class UserResponseDTO {

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "로그인 응답")
    public static class UserLoginResponseDTO {
        @Schema(description = "JWT Access Token", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiaWF0IjoxNzc5MjAwOTA3LCJleHAiOjE3NzkyODczMDd9.example")
        private String accessToken;

        @Schema(description = "JWT Refresh Token", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiaWF0IjoxNzc5MjAwOTA3LCJleHAiOjE3ODA0MTA1MDd9.example")
        private String refreshToken;

        @Schema(description = "토큰 타입", example = "Bearer")
        private String grantType;

        @Schema(description = "Access Token 만료 시각 timestamp", example = "1779287307449")
        private Long expiresIn;

        @Schema(description = "최초 프로필 등록 필요 여부", example = "true")
        private Boolean requiresProfileSetup;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "사용자 프로필 응답")
    public static class UserProfileResponseDTO {
        @Schema(description = "사용자 ID", example = "1")
        private Long userId;

        @Schema(description = "사용자 닉네임", example = "은서")
        private String nickname;

        @Schema(description = "나이", example = "24")
        private Integer age;

        @Schema(description = "키(cm)", example = "164.5")
        private Float heightCm;

        @Schema(description = "몸무게(kg)", example = "52.3")
        private Float weightKg;

        @Schema(description = "발사이즈(mm)", example = "240")
        private Integer footSize;

        @Schema(description = "성별", example = "FEMALE", allowableValues = {"MALE", "FEMALE"})
        private String gender;

        @Schema(description = "현재 연결된 디바이스 존재 여부", example = "true")
        private Boolean deviceConnected;

        @Schema(description = "최초 프로필 등록 필요 여부", example = "false")
        private Boolean requiresProfileSetup;
    }
}
