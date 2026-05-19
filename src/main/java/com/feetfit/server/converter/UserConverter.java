package com.feetfit.server.converter;

import com.feetfit.server.domain.User;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.domain.enums.UserStatus;
import com.feetfit.server.web.dto.user.UserResponseDTO;

public class UserConverter {

    public static User toKakaoUser(String socialId, String nickname) {
        return User.builder()
                .socialId(socialId)
                .socialType(SocialType.KAKAO)
                .nickname(nickname != null ? nickname : "카카오사용자")
                .status(UserStatus.ACTIVE)
                .build();
    }

    public static UserResponseDTO.UserLoginResponseDTO toUserLoginResponseDTO(
            User user,
            String accessToken,
            String refreshToken,
            Long expiresIn
    ) {
        return UserResponseDTO.UserLoginResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .grantType("Bearer")
                .expiresIn(expiresIn)
                .requiresProfileSetup(requiresProfileSetup(user))
                .build();
    }

    public static UserResponseDTO.UserProfileResponseDTO toUserProfileResponseDTO(User user) {
        return UserResponseDTO.UserProfileResponseDTO.builder()
                .userId(user.getId())
                .nickname(user.getNickname())
                .age(user.getAge())
                .heightCm(user.getHeightCm())
                .weightKg(user.getWeightKg())
                .footSize(user.getFootSize())
                .gender(user.getGender() != null ? user.getGender().name() : null)
                .requiresProfileSetup(requiresProfileSetup(user))
                .build();
    }

    public static boolean requiresProfileSetup(User user) {
        return user.getAge() == null
                || user.getHeightCm() == null
                || user.getWeightKg() == null
                || user.getFootSize() == null
                || user.getGender() == null;
    }
}
