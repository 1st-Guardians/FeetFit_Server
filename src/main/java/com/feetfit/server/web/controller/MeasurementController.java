package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.ApiResponse;
import com.feetfit.server.domain.enums.MeasurementFailureReason;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.jwt.FindLoginUser;
import com.feetfit.server.service.MeasurementService.MeasurementCommandService;
import com.feetfit.server.service.MeasurementService.MeasurementQueryService;
import com.feetfit.server.service.MeasurementService.MeasurementSocketService;
import com.feetfit.server.web.dto.measurement.MeasurementRequestDTO;
import com.feetfit.server.web.dto.measurement.MeasurementResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Measurement", description = "측정 세션 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/measurement-sessions")
public class MeasurementController {

    private final MeasurementCommandService measurementCommandService;
    private final MeasurementQueryService measurementQueryService;
    private final MeasurementSocketService measurementSocketService;
    private final FindLoginUser findLoginUser;

    @PostMapping
    @Operation(
            summary = "측정 세션 생성 [민지]",
            description = """
                    새로운 측정 세션을 생성합니다.
                    Authorization 헤더에 Bearer accessToken이 필요합니다.
                    - 요청 바디는 없습니다.
                    - 생성 시 status는 자동으로 WAITING_FOR_PHOTO로 설정됩니다.
                    - 로그인 사용자에게 연결된 디바이스로 측정을 시작합니다.
                    - WebSocket 연결 주소: /ws/measurements
                    - 측정 상태 구독 topic: /topic/measurements/{measurementSessionId}
                    - 사용자 단위 측정 상태 구독 topic: /topic/users/{userId}/measurements
                    - 응답의 webSocketTopic은 측정 세션별 구독 방 이름입니다.
                    - 세션 생성 후 프론트가 구독 중인 WebSocket topic으로 WAITING_FOR_PHOTO 상태 메시지를 발행합니다.
                    - 세션 생성 직후에는 하드웨어 작업 요청을 보내지 않습니다.
                    - 하드웨어 작업 요청은 상태 업데이트 시점에 단계별 API로 전달합니다.
                    - READY_FOR_PHOTO: 하드웨어 사진 촬영 API 호출
                    - READY_FOR_PRESSURE: 하드웨어 압력 및 온습도 측정 API 호출
                    - 하드웨어 요청에는 프론트가 보낸 Authorization 헤더 값을 그대로 전달합니다.
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
                    description = "잘못된 요청 또는 기기 미연결",
                    content = @Content(examples = {
                            @ExampleObject(name = "잘못된 요청", value = VALIDATION_ERROR_RESPONSE),
                            @ExampleObject(name = "기기 미연결", value = DEVICE_NOT_CONNECTED_RESPONSE)
                    })
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))
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
            @Parameter(hidden = true) HttpServletRequest request
    ) {
        Long userId = findLoginUser.getCurrentUserId();
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        return ApiResponse.onSuccess(measurementCommandService.createMeasurementSession(userId, authorizationHeader));
    }

    @PostMapping("/socket-test")
    @Operation(
            summary = "측정 WebSocket 테스트 메시지 발행 [은서]",
            description = """
                    프론트 WebSocket 연결 및 구독 여부를 확인하기 위한 테스트 API입니다.
                    Authorization 헤더에 Bearer accessToken이 필요합니다.
                    - 프론트는 먼저 /ws/measurements에 연결합니다.
                    - /topic/users/{userId}/measurements를 구독합니다.
                    - 이 API를 호출하면 해당 사용자 topic으로 SOCKET_TEST 메시지를 발행합니다.
                    """
    )
    public ApiResponse<String> sendMeasurementSocketTestMessage() {
        Long userId = findLoginUser.getCurrentUserId();
        measurementSocketService.sendTestMessage(userId);
        return ApiResponse.onSuccess("/topic/users/" + userId + "/measurements");
    }

    @PatchMapping("/{measurementSessionId}/status")
    @Operation(
            summary = "측정 세션 상태 업데이트 [민지]",
            description = """
                측정 세션의 상태를 업데이트합니다.
                Authorization 헤더에 Bearer accessToken이 필요합니다.
                - WAITING_FOR_PHOTO: 사진 촬영 준비 대기. FSR 센서 판을 올리고 유리판 위에 올라와야 하는 상태입니다.
                - READY_FOR_PHOTO: 사진 촬영 준비 완료. 프론트가 사용자의 준비 완료 버튼 입력 후 보내는 상태입니다.
                - CAPTURING_PHOTO: 발 사진 촬영 중. 사용자가 움직이지 않아야 하는 상태입니다.
                - WAITING_FOR_PRESSURE: 사진 촬영 완료 후 압력 측정 준비 대기. 사용자가 내려와 FSR 센서 판을 내리고 다시 올라와야 하는 상태입니다.
                - READY_FOR_PRESSURE: 압력 및 온습도 측정 준비 완료. 프론트가 사용자의 준비 완료 버튼 입력 후 보내는 상태입니다.
                - MEASURING_PRESSURE: FSR 압력 측정 중. 사용자가 움직이지 않아야 하는 상태입니다.
                - ANALYZING: 모든 측정 수집 완료 후 분석 중인 상태입니다.
                - COMPLETED: 완료 조건 검사 요청입니다. 백엔드가 내부 완료 플래그를 확인해 모두 완료된 경우에만 최종 완료 처리합니다.
                - FAILED: 측정 실패. 측정 중 오류가 발생한 상태입니다.
                - READY_FOR_PHOTO 요청이 들어오면 백엔드는 트랜잭션 커밋 후 하드웨어 사진 촬영 API를 호출합니다.
                - READY_FOR_PRESSURE 요청이 들어오면 백엔드는 트랜잭션 커밋 후 하드웨어 압력 및 온습도 측정 API를 호출합니다.
                - ANALYZING 요청은 분석 중 상태만 갱신하며 하드웨어 API를 호출하지 않습니다.
                - 같은 상태가 중복으로 들어오면 같은 하드웨어 작업 요청은 다시 보내지 않습니다.
                - FAILED 요청 시 failureReason, failureDetail을 함께 보내면 측정 세션에 실패 원인을 저장하고 failureMessage와 함께 WebSocket으로 프론트에 전달합니다.
                - 상태 업데이트 성공 시 WebSocket으로 상태 메시지를 발행합니다. 단, COMPLETED 요청은 모든 완료 조건이 충족된 경우에만 완료 WebSocket을 발행합니다.
                - COMPLETED/FAILED 메시지의 shouldDisconnect=true를 받은 프론트는 측정 세션 topic 구독을 해제하거나 WebSocket 연결을 종료하면 됩니다.
                - PENDING/MEASURING/TRANSFERRING은 기존 DB 데이터 호환용 상태입니다.
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
                    description = "요청 파라미터 오류",
                    content = @Content(examples = @ExampleObject(value = MEASUREMENT_BAD_REQUEST_RESPONSE))
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
            @Parameter(hidden = true) HttpServletRequest httpRequest,
            @PathVariable Long measurementSessionId,
            @Parameter(description = "측정 세션 상태", example = "READY_FOR_PHOTO")
            @RequestParam MeasurementStatus status,
            @Parameter(description = "측정 소요 시간 (초). COMPLETED 시 생략하면 자동 계산", example = "180")
            @RequestParam(required = false) Integer measurementDurationSec,
            @Parameter(description = "측정 실패 원인. status=FAILED일 때 사용", example = "CAMERA_ERROR")
            @RequestParam(required = false) MeasurementFailureReason failureReason,
            @Parameter(description = "측정 실패 상세 설명. status=FAILED일 때 사용", example = "Camera timeout")
            @RequestParam(required = false) String failureDetail
    ) {
        Long userId = findLoginUser.getCurrentUserId();
        MeasurementRequestDTO.UpdateMeasurementStatusDTO request =
                new MeasurementRequestDTO.UpdateMeasurementStatusDTO(
                        status,
                        measurementDurationSec,
                        failureReason,
                        failureDetail
                );
        String authorizationHeader = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
        return ApiResponse.onSuccess(measurementCommandService.updateMeasurementStatus(
                userId, measurementSessionId, request, authorizationHeader));
    }

    @DeleteMapping("/{measurementSessionId}/records")
    @Operation(
            summary = "측정 세션 및 연관 기록 삭제 [은서]",
            description = """
                개발/테스트용 API입니다.
                Authorization 헤더 없이 호출 가능합니다.
                measurementSessionId에 연결된 분석 결과, 센서 기록, 리포트, 측정 세션을 자식 테이블부터 순서대로 삭제합니다.
                """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "측정 세션 및 연관 기록 삭제 성공",
                    content = @Content(examples = @ExampleObject(value = DELETE_MEASUREMENT_RECORDS_SUCCESS_RESPONSE))
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
    public ApiResponse<MeasurementResponseDTO.DeleteMeasurementRecordsResultDTO> deleteMeasurementRecords(
            @PathVariable Long measurementSessionId
    ) {
        return ApiResponse.onSuccess(measurementCommandService.deleteMeasurementRecords(measurementSessionId));
    }

    private static final String MEASUREMENT_BAD_REQUEST_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON400",
              "message": "잘못된 요청입니다.",
              "result": null
            }
            """;

    private static final String DELETE_MEASUREMENT_RECORDS_SUCCESS_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "measurementSessionId": 2,
                "deletedMetricAnalysisResultCount": 5,
                "deletedReportCount": 1,
                "deletedHalluxValgusAnalysisCount": 1,
                "deletedTinaPedisAnalysisCount": 1,
                "deletedDailyFootAnalysisCount": 1,
                "deletedPlantarFootprintCount": 1,
                "deletedStaticPressureAnalysisCount": 1,
                "deletedFootEnvironmentAnalysisCount": 1,
                "deletedFootOdorAnalysisCount": 1,
                "deletedPressureSensorValueCount": 24,
                "deletedPressureSensorReadingCount": 10,
                "deletedFootEnvironmentReadingCount": 10,
                "deletedFootOdorReadingCount": 10,
                "deletedMeasurementAnalysisStatusCount": 1,
                "deletedMeasurementSessionCount": 1
              }
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
                "status": "WAITING_FOR_PHOTO",
                "measuredAt": "2026-05-20T01:55:09",
                "createdAt": "2026-05-20T01:55:09",
                "webSocketTopic": "/topic/measurements/1"
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
                "status": "READY_FOR_PHOTO",
                "measurementDurationSec": null,
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

    private static final String USER_NOT_FOUND_RESPONSE = """
            {
              "isSuccess": false,
              "code": "USER4001",
              "message": "사용자를 찾을 수 없습니다.",
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

    private static final String DEVICE_NOT_CONNECTED_RESPONSE = """
            {
              "isSuccess": false,
              "code": "DEVICE4004",
              "message": "기기가 연결되어 있지 않습니다.",
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
