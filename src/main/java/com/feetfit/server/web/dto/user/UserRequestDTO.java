package com.feetfit.server.web.dto.user;

import com.feetfit.server.domain.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "사용자 프로필 수정 요청")
    public static class UserProfileUpdateRequestDTO {
        @Schema(description = "사용자 닉네임", example = "은서")
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
        private String nickname;

        @Schema(description = "나이", example = "24", minimum = "1", maximum = "120")
        @NotNull(message = "나이는 필수입니다.")
        @Min(value = 1, message = "나이는 1세 이상이어야 합니다.")
        @Max(value = 120, message = "나이는 120세 이하여야 합니다.")
        private Integer age;

        @Schema(description = "키(cm)", example = "164.5", minimum = "30.0", maximum = "250.0")
        @NotNull(message = "키는 필수입니다.")
        @DecimalMin(value = "30.0", message = "키는 30cm 이상이어야 합니다.")
        @DecimalMax(value = "250.0", message = "키는 250cm 이하여야 합니다.")
        private Float heightCm;

        @Schema(description = "몸무게(kg)", example = "52.3", minimum = "1.0", maximum = "300.0")
        @NotNull(message = "몸무게는 필수입니다.")
        @DecimalMin(value = "1.0", message = "몸무게는 1kg 이상이어야 합니다.")
        @DecimalMax(value = "300.0", message = "몸무게는 300kg 이하여야 합니다.")
        private Float weightKg;

        @Schema(description = "발사이즈(mm)", example = "240", minimum = "150", maximum = "350")
        @NotNull(message = "발사이즈는 필수입니다.")
        @Min(value = 150, message = "발사이즈는 150mm 이상이어야 합니다.")
        @Max(value = 350, message = "발사이즈는 350mm 이하여야 합니다.")
        private Integer footSize;

        @Schema(description = "성별", example = "FEMALE", allowableValues = {"MALE", "FEMALE"})
        @NotNull(message = "성별은 필수입니다.")
        private Gender gender;
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "사용자 최초 프로필 등록 요청")
    public static class UserProfileSetupRequestDTO extends UserProfileUpdateRequestDTO {
        @Schema(description = "약관 동의 여부. 최초 프로필 등록 시 반드시 true", example = "true")
        @NotNull(message = "약관 동의 여부는 필수입니다.")
        @AssertTrue(message = "약관 동의는 필수입니다.")
        private Boolean termsAgreed;
    }
}
