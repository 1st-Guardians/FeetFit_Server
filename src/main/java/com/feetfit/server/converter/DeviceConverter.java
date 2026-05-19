package com.feetfit.server.converter;

import com.feetfit.server.domain.Device;
import com.feetfit.server.domain.enums.ConnectionStatus;
import com.feetfit.server.domain.enums.DeviceStatus;
import com.feetfit.server.web.dto.device.DeviceResponseDTO;

public class DeviceConverter {

    public static DeviceResponseDTO.DeviceInfoResponseDTO toDeviceInfoResponseDTO(Device device) {
        return toDeviceInfoResponseDTO(
                device,
                device.getConnectionStatus() == ConnectionStatus.CONNECTED
                        && device.getStatus() == DeviceStatus.REGISTERED
        );
    }

    public static DeviceResponseDTO.DeviceInfoResponseDTO toDeviceInfoResponseDTO(
            Device device,
            Boolean deviceConnected
    ) {
        return DeviceResponseDTO.DeviceInfoResponseDTO.builder()
                .deviceId(device.getId())
                .deviceName(device.getDeviceName())
                .connectionStatus(device.getConnectionStatus().name())
                .status(device.getStatus().name())
                .deviceConnected(deviceConnected)
                .build();
    }

    public static DeviceResponseDTO.DeviceInfoResponseDTO toEmptyDeviceInfoResponseDTO() {
        return DeviceResponseDTO.DeviceInfoResponseDTO.builder()
                .deviceConnected(false)
                .build();
    }

    public static DeviceResponseDTO.DeviceAlreadyRegisteredResponseDTO toDeviceAlreadyRegisteredResponseDTO(
            Device device
    ) {
        return DeviceResponseDTO.DeviceAlreadyRegisteredResponseDTO.builder()
                .deviceId(device.getId())
                .deviceName(device.getDeviceName())
                .build();
    }
}
