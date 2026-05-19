package com.feetfit.server.web.dto.device;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class DeviceResponseDTO {

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceInfoResponseDTO {
        private Long deviceId;
        private String deviceName;
        private String connectionType;
        private String connectionStatus;
        private String status;
        private Boolean deviceConnected;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceAlreadyRegisteredResponseDTO {
        private Long deviceId;
        private String deviceName;
    }
}
