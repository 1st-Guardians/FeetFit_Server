package com.feetfit.server.service.UserService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.UserHandler;
import com.feetfit.server.converter.UserConverter;
import com.feetfit.server.domain.User;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.web.dto.user.UserRequestDTO;
import com.feetfit.server.web.dto.user.UserResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public UserResponseDTO.UserProfileResponseDTO getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));

        return UserConverter.toUserProfileResponseDTO(user);
    }

    @Override
    @Transactional
    public UserResponseDTO.UserProfileResponseDTO updateProfile(
            Long userId,
            UserRequestDTO.UserProfileUpdateRequestDTO request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));

        user.updateProfile(
                request.getNickname(),
                request.getAge(),
                request.getHeightCm(),
                request.getWeightKg(),
                request.getGender(),
                request.getProfileImageUrl()
        );

        return UserConverter.toUserProfileResponseDTO(user);
    }
}
