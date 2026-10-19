package com.feetfit.server.service.OAuthService;

import com.feetfit.server.web.dto.user.UserResponseDTO;

public interface AppleLoginCommandService {

    UserResponseDTO.UserLoginResponseDTO login(String authorizationCode);
}
