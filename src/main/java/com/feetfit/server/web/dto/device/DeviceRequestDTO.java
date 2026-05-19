package com.feetfit.server.web.dto.device;

import com.feetfit.server.domain.enums.ConnectionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class DeviceRequestDTO {

    @Getter
    @NoArgsConstructor
    @Schema(description = "디바이스 연결 요청")
    public static class DeviceConnectRequestDTO {
        @Schema(description = "디바이스 고유 코드", example = "FeetFit-001")
        @NotBlank(message = "디바이스 고유 코드는 필수입니다.")
        @Size(max = 100, message = "디바이스 고유 코드는 100자 이하여야 합니다.")
        private String deviceName;

        @Schema(description = "연결 방식. BLUETOOTH 또는 WIFI만 허용", example = "BLUETOOTH", allowableValues = {"BLUETOOTH", "WIFI"})
        @NotNull(message = "연결 방식은 필수입니다.")
        private ConnectionType connectionType;
    }
}
