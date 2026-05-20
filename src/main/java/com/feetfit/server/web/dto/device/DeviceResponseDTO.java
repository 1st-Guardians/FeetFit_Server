package com.feetfit.server.web.dto.device;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class DeviceResponseDTO {

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "디바이스 연결 상태 응답")
    public static class DeviceInfoResponseDTO {
        @Schema(description = "디바이스 ID. 연결된 디바이스가 없으면 null", example = "1", nullable = true)
        private Long deviceId;

        @Schema(description = "디바이스 고유 코드. 연결된 디바이스가 없으면 null", example = "FeetFit-001", nullable = true)
        private String deviceName;

        @Schema(description = "연결 방식. 연결된 디바이스가 없으면 null", example = "BLUETOOTH", allowableValues = {"BLUETOOTH", "WIFI"}, nullable = true)
        private String connectionType;

        @Schema(description = "디바이스 연결 상태", example = "CONNECTED")
        private String connectionStatus;

        @Schema(description = "디바이스 등록 상태", example = "REGISTERED")
        private String status;

        @Schema(description = "사용자의 디바이스 연결 여부", example = "true")
        private Boolean deviceConnected;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "이미 연결된 디바이스 정보 응답")
    public static class DeviceAlreadyRegisteredResponseDTO {
        @Schema(description = "이미 연결된 디바이스 ID", example = "1")
        private Long deviceId;

        @Schema(description = "이미 연결된 디바이스 고유 코드", example = "FeetFit-001")
        private String deviceName;
    }
}
