package com.feetfit.server.web.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class UserResponseDTO {

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserLoginResponseDTO {
        private String accessToken;
        private String refreshToken;
        private String grantType;
        private Long expiresIn;
        private Boolean requiresProfileSetup;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserProfileResponseDTO {
        private Long userId;
        private String nickname;
        private Integer age;
        private Float heightCm;
        private Float weightKg;
        private Integer footSize;
        private String gender;
        private Boolean deviceConnected;
        private Boolean requiresProfileSetup;
    }
}
