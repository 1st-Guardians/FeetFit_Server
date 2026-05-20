package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.ApiResponse;
import com.feetfit.server.jwt.FindLoginUser;
import com.feetfit.server.service.MeasurementService.MeasurementCommandService;
import com.feetfit.server.web.dto.measurement.MeasurementRequestDTO;
import com.feetfit.server.web.dto.measurement.MeasurementResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Measurement", description = "측정 세션 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/measurement-sessions")
public class MeasurementController {

    private final MeasurementCommandService measurementCommandService;
    private final FindLoginUser findLoginUser;

    @PostMapping
    @Operation(
            summary = "측정 세션 생성",
            description = """
                    새로운 측정 세션을 생성합니다.
                    Authorization 헤더에 Bearer accessToken이 필요합니다.
                    - 생성 시 status는 자동으로 PENDING으로 설정됩니다.
                    - 본인의 디바이스 ID만 사용 가능합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "측정 세션 생성 성공",
                    content = @Content(examples = @ExampleObject(value = CREATE_MEASUREMENT_SESSION_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "필수값 누락",
                    content = @Content(examples = @ExampleObject(value = VALIDATION_ERROR_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "본인의 디바이스가 아님",
                    content = @Content(examples = @ExampleObject(value = DEVICE_FORBIDDEN_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "디바이스를 찾을 수 없음",
                    content = @Content(examples = @ExampleObject(value = DEVICE_NOT_FOUND_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<MeasurementResponseDTO.CreateMeasurementSessionResultDTO> createMeasurementSession(
            @RequestBody @Valid MeasurementRequestDTO.CreateMeasurementSessionDTO request
    ) {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(measurementCommandService.createMeasurementSession(userId, request));
    }

    @PatchMapping("/{measurementSessionId}/status")
    @Operation(
            summary = "측정 세션 상태 업데이트",
            description = """
                    측정 세션의 상태를 업데이트합니다.
                    Authorization 헤더에 Bearer accessToken이 필요합니다.
                    - status: PENDING, MEASURING, TRANSFERRING, COMPLETED, FAILED
                    - COMPLETED 시 measurementDurationSec 함께 전달해주세요.
                    - 본인의 측정 세션 ID만 사용 가능합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "측정 세션 상태 업데이트 성공",
                    content = @Content(examples = @ExampleObject(value = UPDATE_MEASUREMENT_STATUS_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "본인의 측정 세션이 아님",
                    content = @Content(examples = @ExampleObject(value = MEASUREMENT_FORBIDDEN_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "측정 세션을 찾을 수 없음",
                    content = @Content(examples = @ExampleObject(value = MEASUREMENT_NOT_FOUND_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<MeasurementResponseDTO.UpdateMeasurementStatusResultDTO> updateMeasurementStatus(
            @PathVariable Long measurementSessionId,
            @RequestBody @Valid MeasurementRequestDTO.UpdateMeasurementStatusDTO request
    ) {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(measurementCommandService.updateMeasurementStatus(userId, measurementSessionId, request));
    }

    private static final String CREATE_MEASUREMENT_SESSION_SUCCESS_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "id": 1,
                "deviceId": 1,
                "status": "PENDING",
                "measuredAt": "2026-05-20T01:55:09",
                "createdAt": "2026-05-20T01:55:09"
              }
            }
            """;

    private static final String UPDATE_MEASUREMENT_STATUS_SUCCESS_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "id": 1,
                "status": "COMPLETED",
                "measurementDurationSec": 30,
                "updatedAt": "2026-05-20T02:00:09"
              }
            }
            """;

    private static final String VALIDATION_ERROR_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON400",
              "message": "잘못된 요청입니다.",
              "result": {
                "deviceId": "디바이스 ID는 필수입니다."
              }
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

    private static final String DEVICE_FORBIDDEN_RESPONSE = """
            {
              "isSuccess": false,
              "code": "DEVICE4003",
              "message": "본인의 디바이스가 아닙니다.",
              "result": null
            }
            """;

    private static final String MEASUREMENT_FORBIDDEN_RESPONSE = """
            {
              "isSuccess": false,
              "code": "MEASUREMENT4003",
              "message": "본인의 측정 세션이 아닙니다.",
              "result": null
            }
            """;

    private static final String DEVICE_NOT_FOUND_RESPONSE = """
            {
              "isSuccess": false,
              "code": "DEVICE4001",
              "message": "디바이스를 찾을 수 없습니다.",
              "result": null
            }
            """;

    private static final String MEASUREMENT_NOT_FOUND_RESPONSE = """
            {
              "isSuccess": false,
              "code": "MEASUREMENT4001",
              "message": "측정 세션을 찾을 수 없습니다.",
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