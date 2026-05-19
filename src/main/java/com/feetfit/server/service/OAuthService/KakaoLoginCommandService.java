package com.feetfit.server.service.OAuthService;

import com.feetfit.server.web.dto.user.UserResponseDTO;

public interface KakaoLoginCommandService {
    UserResponseDTO.UserLoginResponseDTO login(String accessToken);
}
