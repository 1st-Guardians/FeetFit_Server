package com.feetfit.server.web.dto.user;

import com.feetfit.server.domain.enums.Gender;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class UserRequestDTO {

    @Getter
    @NoArgsConstructor
    public static class UserProfileUpdateRequestDTO {
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
        private String nickname;

        @NotNull(message = "나이는 필수입니다.")
        @Min(value = 1, message = "나이는 1세 이상이어야 합니다.")
        @Max(value = 120, message = "나이는 120세 이하여야 합니다.")
        private Integer age;

        @NotNull(message = "키는 필수입니다.")
        @DecimalMin(value = "30.0", message = "키는 30cm 이상이어야 합니다.")
        @DecimalMax(value = "250.0", message = "키는 250cm 이하여야 합니다.")
        private Float heightCm;

        @NotNull(message = "몸무게는 필수입니다.")
        @DecimalMin(value = "1.0", message = "몸무게는 1kg 이상이어야 합니다.")
        @DecimalMax(value = "300.0", message = "몸무게는 300kg 이하여야 합니다.")
        private Float weightKg;

        @NotNull(message = "성별은 필수입니다.")
        private Gender gender;

        @Size(max = 255, message = "프로필 이미지 URL은 255자 이하여야 합니다.")
        private String profileImageUrl;
    }

    @Getter
    @NoArgsConstructor
    public static class UserProfileSetupRequestDTO extends UserProfileUpdateRequestDTO {
        @NotNull(message = "약관 동의 여부는 필수입니다.")
        @AssertTrue(message = "약관 동의는 필수입니다.")
        private Boolean termsAgreed;
    }
}
