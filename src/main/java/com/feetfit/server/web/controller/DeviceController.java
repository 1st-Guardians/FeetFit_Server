package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.ApiResponse;
import com.feetfit.server.jwt.FindLoginUser;
import com.feetfit.server.service.DeviceService.DeviceService;
import com.feetfit.server.web.dto.device.DeviceRequestDTO;
import com.feetfit.server.web.dto.device.DeviceResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Device", description = "기기 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final FindLoginUser findLoginUser;

    @PostMapping("/connect")
    @Operation(
            summary = "디바이스 연결",
            description = """
                    디바이스 고유 코드(deviceName)로 기존 디바이스를 로그인한 사용자에게 연결합니다.
                    사용자는 연결된 디바이스를 하나만 가질 수 있습니다.
                    connectionType은 BLUETOOTH 또는 WIFI만 허용합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "디바이스 연결 성공",
                    content = @Content(examples = @ExampleObject(value = DEVICE_CONNECTED_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "deviceName 누락, 빈 문자열, 길이 초과, 잘못된 JSON",
                    content = @Content(examples = {
                            @ExampleObject(name = "유효성 검사 실패", value = DEVICE_CONNECT_VALIDATION_ERROR_RESPONSE),
                            @ExampleObject(name = "잘못된 JSON", value = INVALID_JSON_RESPONSE)
                    })
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "접근 권한 없음",
                    content = @Content(examples = @ExampleObject(value = FORBIDDEN_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "토큰의 userId에 해당하는 유저가 없거나, deviceName에 해당하는 기기가 없거나, 사용 불가능한 기기",
                    content = @Content(examples = {
                            @ExampleObject(name = "유저 없음", value = USER_NOT_FOUND_RESPONSE),
                            @ExampleObject(name = "기기 없음", value = DEVICE_NOT_FOUND_RESPONSE)
                    })
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "사용자에게 이미 등록된 기기가 있음",
                    content = @Content(examples = @ExampleObject(value = DEVICE_ALREADY_REGISTERED_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<DeviceResponseDTO.DeviceInfoResponseDTO> connectDevice(
            @RequestBody @Valid DeviceRequestDTO.DeviceConnectRequestDTO request
    ) {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(deviceService.connectDevice(userId, request));
    }

    @GetMapping
    @Operation(
            summary = "내 디바이스 조회 [은서]",
            description = "로그인한 사용자의 연결된 디바이스 정보를 조회합니다. 연결된 디바이스가 없으면 에러가 아니라 deviceConnected=false로 응답합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "내 디바이스 조회 성공. 연결된 기기가 없어도 성공 응답",
                    content = @Content(examples = {
                            @ExampleObject(name = "연결된 기기 있음", value = DEVICE_CONNECTED_SUCCESS_RESPONSE),
                            @ExampleObject(name = "연결된 기기 없음", value = DEVICE_NOT_CONNECTED_SUCCESS_RESPONSE)
                    })
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "접근 권한 없음",
                    content = @Content(examples = @ExampleObject(value = FORBIDDEN_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "토큰의 userId에 해당하는 유저가 없음",
                    content = @Content(examples = @ExampleObject(value = USER_NOT_FOUND_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<DeviceResponseDTO.DeviceInfoResponseDTO> getMyDevice() {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(deviceService.getMyDevice(userId));
    }

    @DeleteMapping("/{deviceId}/disconnect")
    @Operation(
            summary = "디바이스 연결 해제",
            description = "로그인한 사용자의 연결된 디바이스를 해제합니다. 실제 삭제가 아니라 다시 연결 가능한 AVAILABLE 상태로 변경합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "디바이스 연결 해제 성공",
                    content = @Content(examples = @ExampleObject(value = DEVICE_DISCONNECTED_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "접근 권한 없음",
                    content = @Content(examples = @ExampleObject(value = FORBIDDEN_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "토큰의 userId에 해당하는 유저가 없거나, 연결된 기기가 없거나, 요청한 deviceId가 내 기기가 아님",
                    content = @Content(examples = {
                            @ExampleObject(name = "유저 없음", value = USER_NOT_FOUND_RESPONSE),
                            @ExampleObject(name = "기기 없음", value = DEVICE_NOT_FOUND_RESPONSE)
                    })
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<DeviceResponseDTO.DeviceInfoResponseDTO> disconnectDevice(
            @PathVariable Long deviceId
    ) {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(deviceService.disconnectDevice(userId, deviceId));
    }

    private static final String DEVICE_CONNECTED_SUCCESS_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "deviceId": 1,
                "deviceName": "FeetFit-001",
                "connectionType": "BLUETOOTH",
                "connectionStatus": "CONNECTED",
                "status": "REGISTERED",
                "deviceConnected": true
              }
            }
            """;

    private static final String DEVICE_NOT_CONNECTED_SUCCESS_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "deviceId": null,
                "deviceName": null,
                "connectionType": null,
                "connectionStatus": null,
                "status": null,
                "deviceConnected": false
              }
            }
            """;

    private static final String DEVICE_DISCONNECTED_SUCCESS_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "deviceId": 1,
                "deviceName": "FeetFit-001",
                "connectionType": "BLUETOOTH",
                "connectionStatus": "DISCONNECTED",
                "status": "AVAILABLE",
                "deviceConnected": false
              }
            }
            """;

    private static final String DEVICE_CONNECT_VALIDATION_ERROR_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON400",
              "message": "잘못된 요청입니다.",
              "result": {
                "deviceName": "디바이스 고유 코드는 필수입니다.",
                "connectionType": "연결 방식은 필수입니다."
              }
            }
            """;

    private static final String INVALID_JSON_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON400",
              "message": "요청 본문 형식이 잘못되었습니다. enum 값은 허용된 대문자 값으로 입력해야 합니다.",
              "result": null
            }
            """;

    private static final String DEVICE_ALREADY_REGISTERED_RESPONSE = """
            {
              "isSuccess": false,
              "code": "DEVICE4002",
              "message": "이미 등록된 기기가 있습니다.",
              "result": {
                "deviceId": 1,
                "deviceName": "FeetFit-001"
              }
            }
            """;

    private static final String DEVICE_NOT_FOUND_RESPONSE = """
            {
              "isSuccess": false,
              "code": "DEVICE4001",
              "message": "기기를 찾을 수 없습니다.",
              "result": null
            }
            """;

    private static final String USER_NOT_FOUND_RESPONSE = """
            {
              "isSuccess": false,
              "code": "USER4001",
              "message": "사용자를 찾을 수 없습니다.",
              "result": null
            }
            """;

    private static final String UNAUTHORIZED_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON401",
              "message": "인증이 필요합니다.",
              "result": null
            }
            """;

    private static final String FORBIDDEN_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON403",
              "message": "금지된 요청입니다.",
              "result": null
            }
            """;

    private static final String INTERNAL_SERVER_ERROR_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON500",
              "message": "서버 에러, 관리자에게 문의 바랍니다.",
              "result": null
            }
            """;
}
