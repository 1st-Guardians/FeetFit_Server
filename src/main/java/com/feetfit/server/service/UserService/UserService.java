package com.feetfit.server.service.UserService;

import com.feetfit.server.web.dto.user.UserRequestDTO;
import com.feetfit.server.web.dto.user.UserResponseDTO;

public interface UserService {
    UserResponseDTO.UserProfileResponseDTO getProfile(Long userId);

    UserResponseDTO.UserProfileResponseDTO updateProfile(Long userId, UserRequestDTO.UserProfileUpdateRequestDTO request);
}
