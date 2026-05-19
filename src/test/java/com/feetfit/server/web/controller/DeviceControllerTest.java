package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.ExceptionAdvice;
import com.feetfit.server.apiPayload.exception.handler.DeviceHandler;
import com.feetfit.server.jwt.FindLoginUser;
import com.feetfit.server.jwt.TokenProvider;
import com.feetfit.server.service.DeviceService.DeviceService;
import com.feetfit.server.web.dto.device.DeviceResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeviceController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ExceptionAdvice.class)
class DeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeviceService deviceService;

    @MockBean
    private FindLoginUser findLoginUser;

    @MockBean
    private TokenProvider tokenProvider;

    @MockBean(name = "jpaMappingContext")
    private JpaMetamodelMappingContext jpaMappingContext;

    @Test
    void connectDevice_success_returnsConnectedDevice() throws Exception {
        given(findLoginUser.getCurrentUserId()).willReturn(1L);
        given(deviceService.connectDevice(eq(1L), any())).willReturn(connectedDeviceResponse());

        mockMvc.perform(post("/api/devices/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceName": "FeetFit-001",
                                  "connectionType": "BLUETOOTH"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.result.deviceId").value(1L))
                .andExpect(jsonPath("$.result.deviceName").value("FeetFit-001"))
                .andExpect(jsonPath("$.result.connectionType").value("BLUETOOTH"))
                .andExpect(jsonPath("$.result.connectionStatus").value("CONNECTED"))
                .andExpect(jsonPath("$.result.status").value("REGISTERED"))
                .andExpect(jsonPath("$.result.deviceConnected").value(true));
    }

    @Test
    void connectDevice_blankDeviceName_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/devices/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceName": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.deviceName").value("디바이스 고유 코드는 필수입니다."));
    }

    @Test
    void connectDevice_alreadyConnected_returnsConflictError() throws Exception {
        given(findLoginUser.getCurrentUserId()).willReturn(1L);
        given(deviceService.connectDevice(eq(1L), any()))
                .willThrow(new DeviceHandler(
                        ErrorStatus.DEVICE_ALREADY_REGISTERED,
                        alreadyRegisteredDeviceResponse()
                ));

        mockMvc.perform(post("/api/devices/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceName": "FeetFit-001",
                                  "connectionType": "BLUETOOTH"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("DEVICE4002"))
                .andExpect(jsonPath("$.message").value("이미 등록된 기기가 있습니다."))
                .andExpect(jsonPath("$.result.deviceId").value(1L))
                .andExpect(jsonPath("$.result.deviceName").value("FeetFit-001"));
    }

    @Test
    void getMyDevice_success_returnsRegisteredDevice() throws Exception {
        given(findLoginUser.getCurrentUserId()).willReturn(1L);
        given(deviceService.getMyDevice(1L)).willReturn(connectedDeviceResponse());

        mockMvc.perform(get("/api/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.deviceConnected").value(true));
    }

    @Test
    void getMyDevice_missingDevice_returnsDisconnectedResponse() throws Exception {
        given(findLoginUser.getCurrentUserId()).willReturn(1L);
        given(deviceService.getMyDevice(1L)).willReturn(disconnectedDeviceResponse());

        mockMvc.perform(get("/api/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.result.deviceConnected").value(false));
    }

    @Test
    void disconnectDevice_success_returnsAvailableDevice() throws Exception {
        given(findLoginUser.getCurrentUserId()).willReturn(1L);
        given(deviceService.disconnectDevice(1L, 1L)).willReturn(availableDeviceResponse());

        mockMvc.perform(delete("/api/devices/1/disconnect"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.deviceId").value(1L))
                .andExpect(jsonPath("$.result.connectionType").value("BLUETOOTH"))
                .andExpect(jsonPath("$.result.connectionStatus").value("DISCONNECTED"))
                .andExpect(jsonPath("$.result.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.result.deviceConnected").value(false));
    }

    private static DeviceResponseDTO.DeviceInfoResponseDTO connectedDeviceResponse() {
        return DeviceResponseDTO.DeviceInfoResponseDTO.builder()
                .deviceId(1L)
                .deviceName("FeetFit-001")
                .connectionType("BLUETOOTH")
                .connectionStatus("CONNECTED")
                .status("REGISTERED")
                .deviceConnected(true)
                .build();
    }

    private static DeviceResponseDTO.DeviceInfoResponseDTO availableDeviceResponse() {
        return DeviceResponseDTO.DeviceInfoResponseDTO.builder()
                .deviceId(1L)
                .deviceName("FeetFit-001")
                .connectionType("BLUETOOTH")
                .connectionStatus("DISCONNECTED")
                .status("AVAILABLE")
                .deviceConnected(false)
                .build();
    }

    private static DeviceResponseDTO.DeviceInfoResponseDTO disconnectedDeviceResponse() {
        return DeviceResponseDTO.DeviceInfoResponseDTO.builder()
                .deviceConnected(false)
                .build();
    }

    private static DeviceResponseDTO.DeviceAlreadyRegisteredResponseDTO alreadyRegisteredDeviceResponse() {
        return DeviceResponseDTO.DeviceAlreadyRegisteredResponseDTO.builder()
                .deviceId(1L)
                .deviceName("FeetFit-001")
                .build();
    }
}
