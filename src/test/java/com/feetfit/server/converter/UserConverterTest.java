package com.feetfit.server.converter;

import com.feetfit.server.domain.User;
import com.feetfit.server.domain.enums.Gender;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.domain.enums.UserStatus;
import com.feetfit.server.web.dto.user.UserResponseDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserConverterTest {

    @Test
    void toKakaoUser_nullNickname_usesFallbackNicknameAndRequiresProfileSetup() {
        User user = UserConverter.toKakaoUser("12345", null, "https://example.com/profile.png");

        assertThat(user.getSocialId()).isEqualTo("12345");
        assertThat(user.getSocialType()).isEqualTo(SocialType.KAKAO);
        assertThat(user.getNickname()).isEqualTo("카카오사용자");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(UserConverter.requiresProfileSetup(user)).isTrue();
    }

    @Test
    void toUserLoginResponseDTO_incompleteProfile_setsRequiresProfileSetupTrue() {
        User user = User.builder()
                .id(1L)
                .nickname("은서")
                .socialId("12345")
                .socialType(SocialType.KAKAO)
                .status(UserStatus.ACTIVE)
                .build();

        UserResponseDTO.UserLoginResponseDTO response = UserConverter.toUserLoginResponseDTO(
                user,
                "access-token",
                "refresh-token",
                1000L
        );

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getGrantType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(1000L);
        assertThat(response.getRequiresProfileSetup()).isTrue();
    }

    @Test
    void toUserProfileResponseDTO_completeProfile_setsRequiresProfileSetupFalse() {
        User user = User.builder()
                .id(1L)
                .nickname("은서")
                .age(24)
                .heightCm(165.5F)
                .weightKg(52.3F)
                .gender(Gender.FEMALE)
                .socialId("12345")
                .socialType(SocialType.KAKAO)
                .status(UserStatus.ACTIVE)
                .build();

        UserResponseDTO.UserProfileResponseDTO response = UserConverter.toUserProfileResponseDTO(user);

        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getGender()).isEqualTo("FEMALE");
        assertThat(response.getRequiresProfileSetup()).isFalse();
    }
}
