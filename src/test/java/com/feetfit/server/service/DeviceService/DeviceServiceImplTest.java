package com.feetfit.server.service.DeviceService;

import com.feetfit.server.apiPayload.exception.handler.DeviceHandler;
import com.feetfit.server.domain.Device;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.enums.ConnectionStatus;
import com.feetfit.server.domain.enums.DeviceStatus;
import com.feetfit.server.domain.enums.SocialType;
import com.feetfit.server.domain.enums.UserStatus;
import com.feetfit.server.repository.DeviceConnectionLogRepository;
import com.feetfit.server.repository.DeviceRepository;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.web.dto.device.DeviceRequestDTO;
import com.feetfit.server.web.dto.device.DeviceResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeviceServiceImplTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DeviceConnectionLogRepository deviceConnectionLogRepository;

    @InjectMocks
    private DeviceServiceImpl deviceService;

    @Test
    void connectDevice_success_connectsAvailableDevice() {
        User user = user();
        Device device = availableDevice();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(deviceRepository.findByDeviceName("FeetFit-001")).willReturn(Optional.of(device));

        DeviceResponseDTO.DeviceInfoResponseDTO response = deviceService.connectDevice(1L, connectRequest());

        assertThat(response.getDeviceId()).isEqualTo(1L);
        assertThat(response.getDeviceName()).isEqualTo("FeetFit-001");
        assertThat(response.getConnectionStatus()).isEqualTo("CONNECTED");
        assertThat(response.getStatus()).isEqualTo("REGISTERED");
        assertThat(response.getDeviceConnected()).isTrue();
        assertThat(user.getDevice()).isEqualTo(device);
        verify(deviceConnectionLogRepository).save(argThat(log ->
                log.getUser() == user
                        && log.getDevice() == device
                        && log.getConnectionStatus() == ConnectionStatus.CONNECTED
        ));
    }

    @Test
    void connectDevice_userAlreadyRegistered_throwsDeviceHandler() {
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithDevice(registeredDevice())));

        Throwable throwable = catchThrowable(() -> deviceService.connectDevice(1L, connectRequest()));

        assertThat(throwable).isInstanceOf(DeviceHandler.class);
        assertThat(((DeviceHandler) throwable).getErrorReasonHttpStatus().getMessage())
                .isEqualTo("이미 등록된 기기가 있습니다.");
        assertThat(((DeviceHandler) throwable).getResult())
                .isInstanceOf(DeviceResponseDTO.DeviceAlreadyRegisteredResponseDTO.class)
                .extracting("deviceId", "deviceName")
                .containsExactly(1L, "FeetFit-001");
    }

    @Test
    void connectDevice_registeredDeviceName_allowsMultipleUsersOnSameDevice() {
        User user = user();
        Device device = registeredDevice();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(deviceRepository.findByDeviceName("FeetFit-001")).willReturn(Optional.of(device));

        DeviceResponseDTO.DeviceInfoResponseDTO response = deviceService.connectDevice(1L, connectRequest());

        assertThat(user.getDevice()).isEqualTo(device);
        assertThat(response.getDeviceConnected()).isTrue();
    }

    @Test
    void getMyDevice_existingRegisteredDevice_returnsDevice() {
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithDevice(registeredDevice())));

        DeviceResponseDTO.DeviceInfoResponseDTO response = deviceService.getMyDevice(1L);

        assertThat(response.getDeviceConnected()).isTrue();
    }

    @Test
    void getMyDevice_missingDevice_returnsDisconnectedResponse() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user()));

        DeviceResponseDTO.DeviceInfoResponseDTO response = deviceService.getMyDevice(1L);

        assertThat(response.getDeviceId()).isNull();
        assertThat(response.getDeviceName()).isNull();
        assertThat(response.getConnectionStatus()).isNull();
        assertThat(response.getStatus()).isNull();
        assertThat(response.getDeviceConnected()).isFalse();
    }

    @Test
    void disconnectDevice_existingRegisteredDevice_marksAvailable() {
        Device device = registeredDevice();
        User user = userWithDevice(device);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.countByDeviceId(1L)).willReturn(1L);

        DeviceResponseDTO.DeviceInfoResponseDTO response = deviceService.disconnectDevice(1L, 1L);

        assertThat(user.getDevice()).isNull();
        assertThat(device.getConnectionStatus()).isEqualTo(ConnectionStatus.DISCONNECTED);
        assertThat(device.getStatus()).isEqualTo(DeviceStatus.AVAILABLE);
        assertThat(response.getDeviceConnected()).isFalse();
        verify(deviceConnectionLogRepository).save(argThat(log ->
                log.getUser() == user
                        && log.getDevice() == device
                        && log.getConnectionStatus() == ConnectionStatus.DISCONNECTED
        ));
    }

    @Test
    void disconnectDevice_otherUsersRemain_keepsDeviceRegistered() {
        Device device = registeredDevice();
        User user = userWithDevice(device);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.countByDeviceId(1L)).willReturn(2L);

        DeviceResponseDTO.DeviceInfoResponseDTO response = deviceService.disconnectDevice(1L, 1L);

        assertThat(user.getDevice()).isNull();
        assertThat(device.getConnectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);
        assertThat(device.getStatus()).isEqualTo(DeviceStatus.REGISTERED);
        assertThat(response.getDeviceConnected()).isFalse();
    }

    private static DeviceRequestDTO.DeviceConnectRequestDTO connectRequest() {
        DeviceRequestDTO.DeviceConnectRequestDTO request = new DeviceRequestDTO.DeviceConnectRequestDTO();
        ReflectionTestUtils.setField(request, "deviceName", "FeetFit-001");
        return request;
    }

    private static Device availableDevice() {
        return Device.builder()
                .id(1L)
                .deviceName("FeetFit-001")
                .connectionStatus(ConnectionStatus.DISCONNECTED)
                .status(DeviceStatus.AVAILABLE)
                .build();
    }

    private static Device registeredDevice() {
        return Device.builder()
                .id(1L)
                .deviceName("FeetFit-001")
                .connectionStatus(ConnectionStatus.CONNECTED)
                .status(DeviceStatus.REGISTERED)
                .build();
    }

    private static User user() {
        return User.builder()
                .id(1L)
                .nickname("은서")
                .socialId("12345")
                .socialType(SocialType.KAKAO)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private static User userWithDevice(Device device) {
        User user = user();
        user.connectDevice(device);
        return user;
    }
}
