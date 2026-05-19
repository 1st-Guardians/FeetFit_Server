package com.feetfit.server.service.UserService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.UserHandler;
import com.feetfit.server.converter.UserConverter;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.UserTerms;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.repository.UserTermsRepository;
import com.feetfit.server.web.dto.user.UserRequestDTO;
import com.feetfit.server.web.dto.user.UserResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserTermsRepository userTermsRepository;

    @Override
    public UserResponseDTO.UserProfileResponseDTO getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));

        return UserConverter.toUserProfileResponseDTO(user);
    }

    @Override
    @Transactional
    public UserResponseDTO.UserProfileResponseDTO setupProfile(
            Long userId,
            UserRequestDTO.UserProfileSetupRequestDTO request
    ) {
        User user = findUser(userId);
        if (!UserConverter.requiresProfileSetup(user)) {
            throw new UserHandler(ErrorStatus.USER_PROFILE_ALREADY_SETUP);
        }

        updateProfileFields(user, request);
        LocalDateTime now = LocalDateTime.now();
        userTermsRepository.save(UserTerms.builder()
                .user(user)
                .isAgreed(true)
                .agreedAt(now)
                .expiresAt(now.plusYears(1))
                .build());

        return UserConverter.toUserProfileResponseDTO(user);
    }

    @Override
    @Transactional
    public UserResponseDTO.UserProfileResponseDTO updateProfile(
            Long userId,
            UserRequestDTO.UserProfileUpdateRequestDTO request
    ) {
        User user = findUser(userId);

        updateProfileFields(user, request);

        return UserConverter.toUserProfileResponseDTO(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));
    }

    private void updateProfileFields(User user, UserRequestDTO.UserProfileUpdateRequestDTO request) {
        user.updateProfile(
                request.getNickname(),
                request.getAge(),
                request.getHeightCm(),
                request.getWeightKg(),
                request.getGender(),
                request.getProfileImageUrl()
        );
    }
}
