package com.feetfit.server.service.DeviceService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.DeviceHandler;
import com.feetfit.server.apiPayload.exception.handler.UserHandler;
import com.feetfit.server.converter.DeviceConverter;
import com.feetfit.server.domain.Device;
import com.feetfit.server.domain.DeviceConnectionLog;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.enums.DeviceStatus;
import com.feetfit.server.repository.DeviceConnectionLogRepository;
import com.feetfit.server.repository.DeviceRepository;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.web.dto.device.DeviceRequestDTO;
import com.feetfit.server.web.dto.device.DeviceResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceServiceImpl implements DeviceService {

    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final DeviceConnectionLogRepository deviceConnectionLogRepository;

    @Override
    @Transactional
    public DeviceResponseDTO.DeviceInfoResponseDTO connectDevice(
            Long userId,
            DeviceRequestDTO.DeviceConnectRequestDTO request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));

        if (user.getDevice() != null) {
            throw new DeviceHandler(
                    ErrorStatus.DEVICE_ALREADY_REGISTERED,
                    DeviceConverter.toDeviceAlreadyRegisteredResponseDTO(user.getDevice())
            );
        }

        Device device = deviceRepository.findByDeviceName(request.getDeviceName())
                .orElseThrow(() -> new DeviceHandler(ErrorStatus.DEVICE_NOT_FOUND));

        if (device.getStatus() == DeviceStatus.INACTIVE || device.getStatus() == DeviceStatus.ERROR) {
            throw new DeviceHandler(ErrorStatus.DEVICE_NOT_FOUND);
        }

        user.connectDevice(device);
        device.markConnected();
        deviceConnectionLogRepository.save(DeviceConnectionLog.connected(user, device, LocalDateTime.now()));
        return DeviceConverter.toDeviceInfoResponseDTO(device, true);
    }

    @Override
    public DeviceResponseDTO.DeviceInfoResponseDTO getMyDevice(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));
        Device device = user.getDevice();
        if (device == null) {
            return DeviceConverter.toEmptyDeviceInfoResponseDTO();
        }

        return DeviceConverter.toDeviceInfoResponseDTO(device, true);
    }

    @Override
    @Transactional
    public DeviceResponseDTO.DeviceInfoResponseDTO disconnectDevice(Long userId, Long deviceId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));
        Device device = user.getDevice();
        if (device == null || !device.getId().equals(deviceId)) {
            throw new DeviceHandler(ErrorStatus.DEVICE_NOT_FOUND);
        }

        long connectedUserCount = userRepository.countByDeviceId(deviceId);
        user.disconnectDevice();
        if (connectedUserCount <= 1) {
            device.markAvailable();
        }
        deviceConnectionLogRepository.save(DeviceConnectionLog.disconnected(user, device, LocalDateTime.now()));
        return DeviceConverter.toDeviceInfoResponseDTO(device, false);
    }
}
