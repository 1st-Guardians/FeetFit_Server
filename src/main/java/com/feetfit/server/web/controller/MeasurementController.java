package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.ApiResponse;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.jwt.FindLoginUser;
import com.feetfit.server.service.MeasurementService.MeasurementCommandService;
import com.feetfit.server.service.MeasurementService.MeasurementQueryService;
import com.feetfit.server.web.dto.measurement.MeasurementRequestDTO;
import com.feetfit.server.web.dto.measurement.MeasurementResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
    private final MeasurementQueryService measurementQueryService;
    private final FindLoginUser findLoginUser;

    @PostMapping
    @Operation(
            summary = "측정 세션 생성 [민지]",
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
            summary = "측정 세션 상태 업데이트 [민지]",
            description = """
                측정 세션의 상태를 업데이트합니다.
                Authorization 헤더에 Bearer accessToken이 필요합니다.
                - PENDING: 측정 대기 중. 측정 세션이 생성된 초기 상태입니다.
                - MEASURING: 측정 진행 중. 기기에서 센서 데이터를 수집하고 있는 상태입니다.
                - TRANSFERRING: 데이터 전송 중. 수집된 데이터를 서버로 전송하고 있는 상태입니다.
                - COMPLETED: 측정 완료. 무지외반, 무좀 분석 결과가 모두 저장된 경우에만 완료 처리가 가능합니다. measurementDurationSec을 함께 전달해주세요.
                - FAILED: 측정 실패. 측정 중 오류가 발생한 상태입니다.
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
                    responseCode = "400",
                    description = "무지외반 또는 무좀 분석 결과 미저장",
                    content = @Content(examples = @ExampleObject(value = MEASUREMENT_ANALYSIS_NOT_READY_RESPONSE))
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
            @Parameter(description = "측정 세션 상태", example = "COMPLETED")
            @RequestParam MeasurementStatus status,
            @Parameter(description = "측정 소요 시간 (초). COMPLETED 시 필수", example = "180")
            @RequestParam(required = false) Integer measurementDurationSec
    ) {
        Long userId = findLoginUser.getCurrentUserId();
        MeasurementRequestDTO.UpdateMeasurementStatusDTO request =
                new MeasurementRequestDTO.UpdateMeasurementStatusDTO(status, measurementDurationSec);
        return ApiResponse.onSuccess(measurementCommandService.updateMeasurementStatus(userId, measurementSessionId, request));
    }

    private static final String MEASUREMENT_ANALYSIS_NOT_READY_RESPONSE = """
        {
          "isSuccess": false,
          "code": "MEASUREMENT4005",
          "message": "무지외반 또는 무좀 분석 결과가 아직 저장되지 않았습니다.",
          "result": null
        }
        """;

    @GetMapping("/today-status")
    @Operation(
            summary = "오늘 측정 여부 조회 [은서]",
            description = """
                    오늘 날짜의 측정 완료 여부를 조회합니다.
                    Authorization 헤더에 Bearer accessToken이 필요합니다.
                    - 기준 시간대는 Asia/Seoul입니다.
                    - COMPLETED 상태의 측정 세션만 측정 기록으로 판단합니다.
                    - 오늘 완료된 측정 기록이 있으면 hasTodayMeasurement=true입니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "오늘 측정 여부 조회 성공",
                    content = @Content(examples = {
                            @ExampleObject(name = "오늘 측정 기록 있음", value = TODAY_MEASUREMENT_STATUS_SUCCESS_RESPONSE),
                            @ExampleObject(name = "오늘 측정 기록 없음", value = TODAY_MEASUREMENT_STATUS_EMPTY_RESPONSE)
                    })
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "토큰의 userId에 해당하는 사용자가 없음",
                    content = @Content(examples = @ExampleObject(value = USER_NOT_FOUND_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<MeasurementResponseDTO.TodayMeasurementStatusResultDTO> getTodayMeasurementStatus() {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(measurementQueryService.getTodayMeasurementStatus(userId));
    }

    @GetMapping("/weekly-status")
    @Operation(
            summary = "한 주 측정 여부 조회 [은서]",
            description = """
                    오늘 날짜가 포함된 일요일~토요일 구간의 날짜별 측정 완료 여부를 조회합니다.
                    Authorization 헤더에 Bearer accessToken이 필요합니다.
                    - 기준 시간대는 Asia/Seoul입니다.
                    - COMPLETED 상태의 측정 세션만 측정 기록으로 판단합니다.
                    - hasWeeklyMeasurement는 이번 주에 완료된 측정 기록이 하나라도 있으면 true입니다.
                    - dailyStatuses는 일요일부터 토요일까지 순서대로 반환합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "한 주 측정 여부 조회 성공",
                    content = @Content(examples = {
                            @ExampleObject(name = "이번 주 측정 기록 있음", value = WEEKLY_MEASUREMENT_STATUS_SUCCESS_RESPONSE),
                            @ExampleObject(name = "이번 주 측정 기록 없음", value = WEEKLY_MEASUREMENT_STATUS_EMPTY_RESPONSE)
                    })
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "토큰의 userId에 해당하는 사용자가 없음",
                    content = @Content(examples = @ExampleObject(value = USER_NOT_FOUND_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<MeasurementResponseDTO.WeeklyMeasurementStatusResultDTO> getWeeklyMeasurementStatus() {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(measurementQueryService.getWeeklyMeasurementStatus(userId));
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

    private static final String TODAY_MEASUREMENT_STATUS_SUCCESS_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "today": "2026-05-20",
                "hasTodayMeasurement": true
              }
            }
            """;

    private static final String TODAY_MEASUREMENT_STATUS_EMPTY_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "today": "2026-05-20",
                "hasTodayMeasurement": false
              }
            }
            """;

    private static final String WEEKLY_MEASUREMENT_STATUS_SUCCESS_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "today": "2026-05-20",
                "weekStartDate": "2026-05-17",
                "weekEndDate": "2026-05-23",
                "hasWeeklyMeasurement": true,
                "dailyStatuses": [
                  {
                    "date": "2026-05-17",
                    "dayOfWeek": "SUNDAY",
                    "dayOfWeekKor": "일",
                    "hasMeasurement": false
                  },
                  {
                    "date": "2026-05-18",
                    "dayOfWeek": "MONDAY",
                    "dayOfWeekKor": "월",
                    "hasMeasurement": true
                  },
                  {
                    "date": "2026-05-19",
                    "dayOfWeek": "TUESDAY",
                    "dayOfWeekKor": "화",
                    "hasMeasurement": false
                  },
                  {
                    "date": "2026-05-20",
                    "dayOfWeek": "WEDNESDAY",
                    "dayOfWeekKor": "수",
                    "hasMeasurement": true
                  },
                  {
                    "date": "2026-05-21",
                    "dayOfWeek": "THURSDAY",
                    "dayOfWeekKor": "목",
                    "hasMeasurement": false
                  },
                  {
                    "date": "2026-05-22",
                    "dayOfWeek": "FRIDAY",
                    "dayOfWeekKor": "금",
                    "hasMeasurement": false
                  },
                  {
                    "date": "2026-05-23",
                    "dayOfWeek": "SATURDAY",
                    "dayOfWeekKor": "토",
                    "hasMeasurement": false
                  }
                ]
              }
            }
            """;

    private static final String WEEKLY_MEASUREMENT_STATUS_EMPTY_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "today": "2026-05-20",
                "weekStartDate": "2026-05-17",
                "weekEndDate": "2026-05-23",
                "hasWeeklyMeasurement": false,
                "dailyStatuses": [
                  {
                    "date": "2026-05-17",
                    "dayOfWeek": "SUNDAY",
                    "dayOfWeekKor": "일",
                    "hasMeasurement": false
                  },
                  {
                    "date": "2026-05-18",
                    "dayOfWeek": "MONDAY",
                    "dayOfWeekKor": "월",
                    "hasMeasurement": false
                  },
                  {
                    "date": "2026-05-19",
                    "dayOfWeek": "TUESDAY",
                    "dayOfWeekKor": "화",
                    "hasMeasurement": false
                  },
                  {
                    "date": "2026-05-20",
                    "dayOfWeek": "WEDNESDAY",
                    "dayOfWeekKor": "수",
                    "hasMeasurement": false
                  },
                  {
                    "date": "2026-05-21",
                    "dayOfWeek": "THURSDAY",
                    "dayOfWeekKor": "목",
                    "hasMeasurement": false
                  },
                  {
                    "date": "2026-05-22",
                    "dayOfWeek": "FRIDAY",
                    "dayOfWeekKor": "금",
                    "hasMeasurement": false
                  },
                  {
                    "date": "2026-05-23",
                    "dayOfWeek": "SATURDAY",
                    "dayOfWeekKor": "토",
                    "hasMeasurement": false
                  }
                ]
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

    private static final String USER_NOT_FOUND_RESPONSE = """
            {
              "isSuccess": false,
              "code": "USER4001",
              "message": "사용자를 찾을 수 없습니다.",
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
