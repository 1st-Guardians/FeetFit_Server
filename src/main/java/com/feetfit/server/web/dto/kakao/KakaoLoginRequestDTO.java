package com.feetfit.server.web.dto.kakao;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class KakaoLoginRequestDTO {
    @NotBlank(message = "카카오 Access Token은 필수입니다.")
    private String accessToken;
}
