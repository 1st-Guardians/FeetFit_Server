package com.feetfit.server.web.dto.kakao;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "카카오 로그인 요청")
public class KakaoLoginRequestDTO {
    @Schema(description = "카카오에서 발급받은 Access Token", example = "kakao_access_token_example_abc123")
    @NotBlank(message = "카카오 Access Token은 필수입니다.")
    private String accessToken;
}
