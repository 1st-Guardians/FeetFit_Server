package com.feetfit.server.service.UserService;

import com.feetfit.server.web.dto.user.UserRequestDTO;
import com.feetfit.server.web.dto.user.UserResponseDTO;

public interface UserService {
    UserResponseDTO.WithdrawUserResultDTO withdraw(Long userId, UserRequestDTO.WithdrawUserRequestDTO request);

    UserResponseDTO.UserProfileResponseDTO getProfile(Long userId);

    UserResponseDTO.UserProfileResponseDTO setupProfile(Long userId, UserRequestDTO.UserProfileSetupRequestDTO request);

    UserResponseDTO.UserProfileResponseDTO updateProfile(Long userId, UserRequestDTO.UserProfileUpdateRequestDTO request);
}
