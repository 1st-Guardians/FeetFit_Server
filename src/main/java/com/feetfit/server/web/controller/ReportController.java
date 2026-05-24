package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.ApiResponse;
import com.feetfit.server.jwt.FindLoginUser;
import com.feetfit.server.service.ReportService.ReportCommandService;
import com.feetfit.server.service.ReportService.ReportQueryService;
import com.feetfit.server.web.dto.report.ReportRequestDTO;
import com.feetfit.server.web.dto.report.ReportResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "Report", description = "리포트 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportCommandService reportCommandService;
    private final ReportQueryService reportQueryService;
    private final FindLoginUser findLoginUser;

    @PostMapping("/hallux-valgus")
    @Operation(
            summary = "무지외반 분석 결과 저장 [민지]",
            description = """
                    AI 분석 결과로 받은 왼발/오른발 무지외반 데이터를 저장합니다.
                    Authorization 헤더에 Bearer accessToken이 필요합니다.
                    - riskLevel은 LOW, MEDIUM, HIGH만 허용합니다.
                    - 같은 날짜에 이미 저장된 데이터가 있으면 덮어씁니다 (UPDATE).
                    - 같은 날짜에 저장된 데이터가 없으면 새로 저장합니다 (INSERT).
                    - 측정 세션의 status가 COMPLETED인 경우에만 저장됩니다.
                    - 본인의 측정 세션 ID만 사용 가능합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "무지외반 분석 결과 저장 성공",
                    content = @Content(examples = @ExampleObject(value = SAVE_HALLUX_VALGUS_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "필수값 누락, 잘못된 enum 값",
                    content = @Content(examples = @ExampleObject(value = VALIDATION_ERROR_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))
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
    public ApiResponse<ReportResponseDTO.SaveHalluxValgusResultDTO> saveHalluxValgusAnalysis(
            @RequestBody @Valid ReportRequestDTO.SaveHalluxValgusDTO request
    ) {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(reportCommandService.saveHalluxValgusAnalysis(userId, request));
    }

    @PostMapping("/tina-pedis")
    @Operation(
            summary = "무좀 분석 결과 저장 [은서]",
            description = """
                    AI 분석 결과로 받은 무좀 분석 데이터를 저장합니다.
                    Authorization 헤더에 Bearer accessToken이 필요합니다.
                    - safetyScore 값은 0 이상 100 이하만 허용합니다.
                    - totalScore는 진균 의심 안전 점수 70%, 피부 반응 안전 점수 30%로 계산됩니다.
                    - 오늘 날짜를 제외한 과거 측정 결과 중 가장 최근 결과가 있으면 previousTotalScore, totalScoreDiff를 함께 반환합니다.
                    - 과거 측정 데이터가 없으면 previousTotalScore, totalScoreDiff는 null을 반환합니다.
                    - 같은 측정 세션에 이미 저장된 데이터가 있으면 덮어씁니다 (UPDATE).
                    - 같은 측정 세션에 저장된 데이터가 없으면 새로 저장합니다 (INSERT).
                    - 측정 세션의 status가 COMPLETED인 경우에만 저장됩니다.
                    - 본인의 측정 세션 ID만 사용 가능합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "무좀 분석 결과 저장 성공",
                    content = @Content(examples = @ExampleObject(value = SAVE_TINA_PEDIS_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "필수값 누락, 점수 범위 오류, 잘못된 JSON, 완료되지 않은 측정 세션",
                    content = @Content(examples = {
                            @ExampleObject(name = "유효성 검사 실패", value = TINA_PEDIS_VALIDATION_ERROR_RESPONSE),
                            @ExampleObject(name = "잘못된 JSON", value = INVALID_JSON_RESPONSE),
                            @ExampleObject(name = "완료되지 않은 측정 세션", value = MEASUREMENT_NOT_COMPLETED_RESPONSE)
                    })
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
    public ApiResponse<ReportResponseDTO.TinaPedisAnalysisResultDTO> saveTinaPedisAnalysis(
            @RequestBody @Valid ReportRequestDTO.SaveTinaPedisAnalysisDTO request
    ) {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(reportCommandService.saveTinaPedisAnalysis(userId, request));
    }

    private static final String SAVE_HALLUX_VALGUS_SUCCESS_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "id": 1,
                "imageUrl": "https://example.com/hallux.jpg",
                "leftToeAngleDegree": 23.5,
                "leftRiskLevel": "MEDIUM",
                "leftAnalysisText": "왼발 무지외반 주의 필요",
                "rightToeAngleDegree": 15.2,
                "rightRiskLevel": "LOW",
                "rightAnalysisText": "오른발 양호",
                "riskScore": 75.5,
                "scoreAnalysisText": "전반적으로 주의가 필요합니다.",
                "createdAt": "2026-05-20T01:55:09",
                "updatedAt": "2026-05-20T01:55:09"
              }
            }
            """;

    private static final String SAVE_TINA_PEDIS_SUCCESS_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "id": 1,
                "measurementSessionId": 1,
                "fungalSuspicionSafetyScore": 82,
                "skinReactionSafetyScore": 76,
                "totalScore": 80.2,
                "previousTotalScore": 74.5,
                "totalScoreDiff": 5.7,
                "fungalSuspicionSafetyDescription": "발가락 사이 일부 영역에서 진균 의심도가 낮게 관찰됩니다.",
                "skinReactionSafetyDescription": "피부 발적과 자극 반응은 경미한 수준입니다.",
                "totalScoreDescription": "전반적으로 안전하지만 발 건조 관리가 필요합니다.",
                "suspiciousAreaMapImageUrl": "https://example.com/tina-pedis/map.png",
                "originalFootImageUrl": "https://example.com/tina-pedis/original.png",
                "recordedAt": "2026-05-20T09:00:00",
                "createdAt": "2026-05-20T09:00:00",
                "updatedAt": "2026-05-20T09:00:00"
              }
            }
            """;

    private static final String VALIDATION_ERROR_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON400",
              "message": "잘못된 요청입니다.",
              "result": {
                "measurementSessionId": "측정 세션 ID는 필수입니다.",
                "riskScore": "종합 위험도 점수는 필수입니다."
              }
            }
            """;

    private static final String TINA_PEDIS_VALIDATION_ERROR_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON400",
              "message": "잘못된 요청입니다.",
              "result": {
                "measurementSessionId": "측정 세션 ID는 필수입니다.",
                "fungalSuspicionSafetyScore": "진균 의심 안전 점수는 필수입니다.",
                "skinReactionSafetyScore": "피부 반응 안전 점수는 필수입니다.",
                "fungalSuspicionSafetyDescription": "진균 의심 안전 설명은 필수입니다.",
                "skinReactionSafetyDescription": "피부 반응 안전 설명은 필수입니다.",
                "totalScoreDescription": "종합 점수 설명은 필수입니다.",
                "suspiciousAreaMapImageUrl": "의심 부위 맵 이미지 URL은 필수입니다.",
                "originalFootImageUrl": "원본 발 이미지 URL은 필수입니다.",
                "recordedAt": "기록 시각은 필수입니다."
              }
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

    @GetMapping("/hallux-valgus")
    @Operation(
            summary = "무지외반 분석 결과 조회 [민지]",
            description = """
                    날짜를 지정하여 해당 날짜의 가장 마지막으로 저장된 무지외반 분석 결과를 조회합니다.
                    Authorization 헤더에 Bearer accessToken이 필요합니다.
                    - date 형식: yyyy-MM-dd
                    - 이전 측정 대비 점수 변화(riskScoreDiff)를 함께 반환합니다.
                    - 이전 측정 데이터가 없으면 previousRiskScore, riskScoreDiff는 null을 반환합니다.
                    - 해당 날짜에 저장된 데이터가 없으면 404를 반환합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "무지외반 분석 결과 조회 성공",
                    content = @Content(examples = @ExampleObject(value = GET_HALLUX_VALGUS_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "해당 날짜에 저장된 무지외반 분석 결과 없음",
                    content = @Content(examples = @ExampleObject(value = HALLUX_VALGUS_NOT_FOUND_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<ReportResponseDTO.HalluxValgusResultDTO> getHalluxValgusAnalysis(
            @Parameter(description = "조회 날짜. yyyy-MM-dd 형식으로 입력합니다.", example = "2026-05-20")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(reportQueryService.getHalluxValgusAnalysis(userId, date));
    }

    @GetMapping("/tina-pedis")
    @Operation(
            summary = "무좀 분석 결과 조회 [은서]",
            description = """
                    날짜를 지정하여 해당 날짜의 가장 마지막으로 기록된 무좀 분석 결과를 조회합니다.
                    Authorization 헤더에 Bearer accessToken이 필요합니다.
                    - date 형식: yyyy-MM-dd
                    - date는 무좀 분석 결과의 recordedAt 기준입니다.
                    - totalScore는 진균 의심 안전 점수 70%, 피부 반응 안전 점수 30%로 계산됩니다.
                    - 오늘 날짜를 제외한 과거 측정 결과 중 가장 최근 결과가 있으면 previousTotalScore, totalScoreDiff를 함께 반환합니다.
                    - 과거 측정 데이터가 없으면 previousTotalScore, totalScoreDiff는 null을 반환합니다.
                    - 해당 날짜에 저장된 무좀 분석 결과가 없으면 404를 반환합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "무좀 분석 결과 조회 성공",
                    content = @Content(examples = @ExampleObject(value = GET_TINA_PEDIS_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "date 파라미터 누락 또는 형식 오류",
                    content = @Content(examples = @ExampleObject(value = INVALID_DATE_PARAMETER_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "해당 날짜에 저장된 무좀 분석 결과 없음",
                    content = @Content(examples = @ExampleObject(value = TINA_PEDIS_NOT_FOUND_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<ReportResponseDTO.TinaPedisAnalysisResultDTO> getTinaPedisAnalysis(
            @Parameter(description = "조회 날짜. yyyy-MM-dd 형식으로 입력합니다.", example = "2026-05-20")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(reportQueryService.getTinaPedisAnalysis(userId, date));
    }

    @PostMapping("/daily-foot-analysis")
    @Operation(
            summary = "종합 발 분석 결과 저장 [민지]",
            description = """
                AI 분석 결과로 받은 종합 발 분석 데이터를 저장합니다.
                Authorization 헤더에 Bearer accessToken이 필요합니다.
                - 같은 측정 세션 ID에 이미 저장된 데이터가 있으면 덮어씁니다 (UPDATE).
                - 같은 측정 세션 ID에 저장된 데이터가 없으면 새로 저장합니다 (INSERT).
                - 측정 세션의 status가 COMPLETED인 경우에만 저장됩니다.
                - 본인의 측정 세션 ID만 사용 가능합니다.
                - careTips는 정확히 3개여야 합니다.
                """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "종합 발 분석 결과 저장 성공",
                    content = @Content(examples = @ExampleObject(value = DAILY_FOOT_ANALYSIS_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "필수값 누락, 완료되지 않은 측정 세션, careTips 3개 아님",
                    content = @Content(examples = {
                            @ExampleObject(name = "유효성 검사 실패", value = DAILY_FOOT_ANALYSIS_VALIDATION_ERROR_RESPONSE),
                            @ExampleObject(name = "완료되지 않은 측정 세션", value = MEASUREMENT_NOT_COMPLETED_RESPONSE)
                    })
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
    public ApiResponse<ReportResponseDTO.DailyFootAnalysisResultDTO> saveDailyFootAnalysis(
            @RequestBody @Valid ReportRequestDTO.SaveDailyFootAnalysisDTO request
    ) {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(reportCommandService.saveDailyFootAnalysis(userId, request));
    }

    @GetMapping("/daily-foot-analysis")
    @Operation(
            summary = "종합 발 분석 결과 조회 [민지]",
            description = """
                날짜를 지정하여 해당 날짜의 가장 마지막으로 저장된 종합 발 분석 결과를 조회합니다.
                Authorization 헤더에 Bearer accessToken이 필요합니다.
                - date 형식: yyyy-MM-dd
                - 이전 측정 대비 균형 점수 변화(balanceScoreDiff)를 함께 반환합니다.
                - 이전 측정 데이터가 없으면 balanceScoreDiff는 null을 반환합니다.
                - 해당 날짜에 저장된 데이터가 없으면 404를 반환합니다.
                """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "종합 발 분석 결과 조회 성공",
                    content = @Content(examples = @ExampleObject(value = DAILY_FOOT_ANALYSIS_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "date 파라미터 누락 또는 형식 오류",
                    content = @Content(examples = @ExampleObject(value = INVALID_DATE_PARAMETER_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "해당 날짜에 저장된 종합 발 분석 결과 없음",
                    content = @Content(examples = @ExampleObject(value = DAILY_FOOT_ANALYSIS_NOT_FOUND_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<ReportResponseDTO.DailyFootAnalysisResultDTO> getDailyFootAnalysis(
            @Parameter(description = "조회 날짜. yyyy-MM-dd 형식으로 입력합니다.", example = "2026-05-20")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(reportQueryService.getDailyFootAnalysis(userId, date));
    }

    @GetMapping("/foot-type-text")
    @Operation(
            summary = "발 타입 텍스트 조회 [민지]",
            description = """
                신발 리스트 페이지에서 사용자의 발 타입 설명 텍스트를 조회합니다.
                Authorization 헤더에 Bearer accessToken이 필요합니다.
                - 가장 최근 종합 발 분석 결과의 typeText를 반환합니다.
                - 측정 이력이 없는 유저는 typeText가 null로 반환됩니다.
                """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "발 타입 텍스트 조회 성공",
                    content = @Content(examples = @ExampleObject(value = FOOT_TYPE_TEXT_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<ReportResponseDTO.FootTypeTextResultDTO> getFootTypeText() {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(reportQueryService.getFootTypeText(userId));
    }

    private static final String FOOT_TYPE_TEXT_SUCCESS_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "nickname": "민지",
                "typeText": "발의 아치가 낮아 발바닥이 넓게 닿는 편이에요. 오래 걷거나 서 있으면 피로가 커질 수 있어 아치를 잘 받쳐주는 신발이 더 편안할 수 있어요."
              }
            }
            """;

    private static final String DAILY_FOOT_ANALYSIS_NOT_FOUND_RESPONSE = """
            {
              "isSuccess": false,
              "code": "REPORT4001",
              "message": "리포트를 찾을 수 없습니다.",
              "result": null
            }
            """;

    private static final String DAILY_FOOT_ANALYSIS_VALIDATION_ERROR_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON400",
              "message": "잘못된 요청입니다.",
              "result": {
                "measurementSessionId": "측정 세션 ID는 필수입니다.",
                "conditionLevel": "종합 상태는 필수입니다.",
                "balanceScore": "균형 점수는 필수입니다.",
                "balanceComment": "균형 코멘트는 필수입니다.",
                "avgTemperatureCelsius": "평균 온도는 필수입니다.",
                "avgHumidityPercent": "평균 습도는 필수입니다.",
                "careTips": "관리 팁은 3개여야 합니다.",
                "typeText": "발 타입 텍스트는 필수입니다."
              }
            }
            """;

    private static final String DAILY_FOOT_ANALYSIS_SUCCESS_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "id": 1,
                "measurementSessionId": 1,
                "conditionLevel": "ATTENTION_NEEDED",
                "conditionComments": [
                  "오른발에 압력이 조금 더 실려 있어요.",
                  "발 냄새 위험도는 낮은 편이에요."
                ],
                "balanceScore": 72.0,
                "balanceComment": "자세 균형에 대한 내용을 적을겁니다.",
                "balanceScoreDiff": 4.0,
                "leftPressurePercent": 46.0,
                "rightPressurePercent": 54.0,
                "leftPressureImageUrl": "https://example.com/left-pressure.jpg",
                "rightPressureImageUrl": "https://example.com/right-pressure.jpg",
                "userFootSize": 250,
                "measuredLeftFootSizeMm": 253.0,
                "measuredRightFootSizeMm": 248.0,
                "leftFootSizeDiff": 3.0,
                "rightFootSizeDiff": -2.0,
                "leftFootWidthMm": 85.0,
                "rightFootWidthMm": 70.0,
                "footOdourPpm": 76.0,
                "footOdourComment": "발 냄새 위험도는 76ppm으로 낮은 편이에요.",
                "avgTemperatureCelsius": 34.0,
                "avgHumidityPercent": 50.0,
                "careTips": [
                  "오른발 앞꿈치 스트레칭을 해주세요.",
                  "신발은 착용 후 충분히 말려주세요.",
                  "발볼이 좁은 신발은 피하는 것이 좋아요."
                ],
                "typeText": "발의 아치가 낮아 발바닥이 넓게 닿는 편이에요. 오래 걷거나 서 있으면 피로가 커질 수 있어 아치를 잘 받쳐주는 신발이 더 편안할 수 있어요.",
                "createdAt": "2026-05-20T09:00:00",
                "updatedAt": "2026-05-20T09:00:00"
              }
            }
            """;

    private static final String GET_HALLUX_VALGUS_SUCCESS_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "id": 1,
                "imageUrl": "https://example.com/hallux.jpg",
                "leftToeAngleDegree": 23.5,
                "leftRiskLevel": "MEDIUM",
                "leftAnalysisText": "왼발 무지외반 주의 필요",
                "rightToeAngleDegree": 15.2,
                "rightRiskLevel": "LOW",
                "rightAnalysisText": "오른발 양호",
                "riskScore": 75.5,
                "scoreAnalysisText": "전반적으로 주의가 필요합니다.",
                "previousRiskScore": 70.0,
                "riskScoreDiff": 5.5,
                "createdAt": "2026-05-20T01:55:09",
                "updatedAt": "2026-05-20T01:55:09"
              }
            }
            """;

    private static final String GET_TINA_PEDIS_SUCCESS_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "id": 1,
                "measurementSessionId": 1,
                "fungalSuspicionSafetyScore": 82,
                "skinReactionSafetyScore": 76,
                "totalScore": 80.2,
                "previousTotalScore": 74.5,
                "totalScoreDiff": 5.7,
                "fungalSuspicionSafetyDescription": "발가락 사이 일부 영역에서 진균 의심도가 낮게 관찰됩니다.",
                "skinReactionSafetyDescription": "피부 발적과 자극 반응은 경미한 수준입니다.",
                "totalScoreDescription": "전반적으로 안전하지만 발 건조 관리가 필요합니다.",
                "suspiciousAreaMapImageUrl": "https://example.com/tina-pedis/map.png",
                "originalFootImageUrl": "https://example.com/tina-pedis/original.png",
                "recordedAt": "2026-05-20T09:00:00",
                "createdAt": "2026-05-20T09:00:00",
                "updatedAt": "2026-05-20T09:00:00"
              }
            }
            """;

    private static final String HALLUX_VALGUS_NOT_FOUND_RESPONSE = """
            {
              "isSuccess": false,
              "code": "REPORT4001",
              "message": "리포트를 찾을 수 없습니다.",
              "result": null
            }
            """;

    private static final String TINA_PEDIS_NOT_FOUND_RESPONSE = """
            {
              "isSuccess": false,
              "code": "TINA_PEDIS4001",
              "message": "무좀 분석 결과를 찾을 수 없습니다.",
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

    private static final String MEASUREMENT_NOT_COMPLETED_RESPONSE = """
            {
              "isSuccess": false,
              "code": "MEASUREMENT4004",
              "message": "완료된 측정 세션이 아닙니다.",
              "result": null
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

    private static final String INVALID_DATE_PARAMETER_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON400",
              "message": "요청 파라미터 형식이 잘못되었습니다.",
              "result": null
            }
            """;

    // 공통 상수

    private static final String UNAUTHORIZED_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON401",
              "message": "인증이 필요합니다.",
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
