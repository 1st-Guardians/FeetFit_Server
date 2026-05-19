package com.feetfit.server.service.DeviceService;

import com.feetfit.server.web.dto.device.DeviceRequestDTO;
import com.feetfit.server.web.dto.device.DeviceResponseDTO;

public interface DeviceService {
    DeviceResponseDTO.DeviceInfoResponseDTO connectDevice(
            Long userId,
            DeviceRequestDTO.DeviceConnectRequestDTO request
    );

    DeviceResponseDTO.DeviceInfoResponseDTO getMyDevice(Long userId);

    DeviceResponseDTO.DeviceInfoResponseDTO disconnectDevice(Long userId, Long deviceId);
}
