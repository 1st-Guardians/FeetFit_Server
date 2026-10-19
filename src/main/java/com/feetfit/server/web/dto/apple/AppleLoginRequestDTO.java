package com.feetfit.server.web.dto.apple;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AppleLoginRequestDTO {

    @Schema(description = "Apple에서 발급받은 일회용 Authorization Code", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Apple Authorization Code는 필수입니다.")
    private String code;
}
