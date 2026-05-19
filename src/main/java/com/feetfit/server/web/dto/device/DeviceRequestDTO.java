package com.feetfit.server.web.dto.device;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class DeviceRequestDTO {

    @Getter
    @NoArgsConstructor
    public static class DeviceConnectRequestDTO {
        @NotBlank(message = "디바이스 고유 코드는 필수입니다.")
        @Size(max = 100, message = "디바이스 고유 코드는 100자 이하여야 합니다.")
        private String deviceName;
    }
}
