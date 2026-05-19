package com.feetfit.server.service.UserService;

import com.feetfit.server.apiPayload.exception.handler.UserHandler;
import com.feetfit.server.domain.Device;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.UserTerms;
import com.feetfit.server.domain.enums.ConnectionType;
import com.feetfit.server.domain.enums.Gender;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.domain.enums.UserStatus;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.repository.UserTermsRepository;
import com.feetfit.server.web.dto.user.UserRequestDTO;
import com.feetfit.server.web.dto.user.UserResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserTermsRepository userTermsRepository;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void getProfile_existingUser_returnsProfileResponse() {
        given(userRepository.findById(1L)).willReturn(Optional.of(completeUserWithDevice()));

        UserResponseDTO.UserProfileResponseDTO response = userService.getProfile(1L);

        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getNickname()).isEqualTo("은서");
        assertThat(response.getAge()).isEqualTo(24);
        assertThat(response.getGender()).isEqualTo("FEMALE");
        assertThat(response.getDeviceConnected()).isTrue();
        assertThat(response.getRequiresProfileSetup()).isFalse();
    }

    @Test
    void getProfile_missingUser_throwsUserHandler() {
        given(userRepository.findById(404L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(404L))
                .isInstanceOf(UserHandler.class);
    }

    @Test
    void updateProfile_existingUser_updatesProfileFields() {
        User user = incompleteUser();
        UserRequestDTO.UserProfileUpdateRequestDTO request = profileUpdateRequest();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        UserResponseDTO.UserProfileResponseDTO response = userService.updateProfile(1L, request);

        assertThat(user.getNickname()).isEqualTo("새닉네임");
        assertThat(user.getAge()).isEqualTo(25);
        assertThat(user.getHeightCm()).isEqualTo(170.5F);
        assertThat(user.getWeightKg()).isEqualTo(60.0F);
        assertThat(user.getFootSize()).isEqualTo(270);
        assertThat(user.getGender()).isEqualTo(Gender.MALE);
        assertThat(response.getDeviceConnected()).isFalse();
        assertThat(response.getRequiresProfileSetup()).isFalse();
    }

    @Test
    void setupProfile_incompleteUser_updatesProfileAndSavesTerms() {
        User user = incompleteUser();
        UserRequestDTO.UserProfileSetupRequestDTO request = profileSetupRequest();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userTermsRepository.save(any(UserTerms.class))).willAnswer(invocation -> invocation.getArgument(0));

        UserResponseDTO.UserProfileResponseDTO response = userService.setupProfile(1L, request);

        assertThat(user.getNickname()).isEqualTo("새닉네임");
        assertThat(user.getAge()).isEqualTo(25);
        assertThat(user.getHeightCm()).isEqualTo(170.5F);
        assertThat(user.getWeightKg()).isEqualTo(60.0F);
        assertThat(user.getFootSize()).isEqualTo(270);
        assertThat(user.getGender()).isEqualTo(Gender.MALE);
        assertThat(response.getRequiresProfileSetup()).isFalse();
        verify(userTermsRepository).save(argThat(userTerms ->
                userTerms.getUser() == user
                        && Boolean.TRUE.equals(userTerms.getIsAgreed())
                        && userTerms.getAgreedAt() != null
                        && userTerms.getExpiresAt() != null
                        && userTerms.getExpiresAt().isAfter(userTerms.getAgreedAt())
        ));
    }

    @Test
    void setupProfile_completeUser_throwsAlreadySetupAndDoesNotSaveTerms() {
        given(userRepository.findById(1L)).willReturn(Optional.of(completeUser()));

        assertThatThrownBy(() -> userService.setupProfile(1L, profileSetupRequest()))
                .isInstanceOf(UserHandler.class);
        verifyNoInteractions(userTermsRepository);
    }

    private static User completeUser() {
        return User.builder()
                .id(1L)
                .nickname("은서")
                .age(24)
                .heightCm(165.5F)
                .weightKg(52.3F)
                .footSize(240)
                .gender(Gender.FEMALE)
                .socialType(SocialType.KAKAO)
                .socialId("12345")
                .status(UserStatus.ACTIVE)
                .build();
    }

    private static User incompleteUser() {
        return User.builder()
                .id(1L)
                .nickname("카카오사용자")
                .socialType(SocialType.KAKAO)
                .socialId("12345")
                .status(UserStatus.ACTIVE)
                .build();
    }

    private static User completeUserWithDevice() {
        User user = completeUser();
        user.connectDevice(
                Device.builder()
                        .id(1L)
                        .deviceName("FeetFit-001")
                        .build(),
                ConnectionType.BLUETOOTH
        );
        return user;
    }

    private static UserRequestDTO.UserProfileUpdateRequestDTO profileUpdateRequest() {
        UserRequestDTO.UserProfileUpdateRequestDTO request = new UserRequestDTO.UserProfileUpdateRequestDTO();
        ReflectionTestUtils.setField(request, "nickname", "새닉네임");
        ReflectionTestUtils.setField(request, "age", 25);
        ReflectionTestUtils.setField(request, "heightCm", 170.5F);
        ReflectionTestUtils.setField(request, "weightKg", 60.0F);
        ReflectionTestUtils.setField(request, "footSize", 270);
        ReflectionTestUtils.setField(request, "gender", Gender.MALE);
        return request;
    }

    private static UserRequestDTO.UserProfileSetupRequestDTO profileSetupRequest() {
        UserRequestDTO.UserProfileSetupRequestDTO request = new UserRequestDTO.UserProfileSetupRequestDTO();
        ReflectionTestUtils.setField(request, "nickname", "새닉네임");
        ReflectionTestUtils.setField(request, "age", 25);
        ReflectionTestUtils.setField(request, "heightCm", 170.5F);
        ReflectionTestUtils.setField(request, "weightKg", 60.0F);
        ReflectionTestUtils.setField(request, "footSize", 270);
        ReflectionTestUtils.setField(request, "gender", Gender.MALE);
        ReflectionTestUtils.setField(request, "termsAgreed", true);
        return request;
    }
}
