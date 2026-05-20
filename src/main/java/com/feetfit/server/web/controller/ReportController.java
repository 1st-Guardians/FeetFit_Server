package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.ApiResponse;
import com.feetfit.server.jwt.FindLoginUser;
import com.feetfit.server.service.ReportService.ReportCommandService;
import com.feetfit.server.service.ReportService.ReportQueryService;
import com.feetfit.server.web.dto.report.ReportRequestDTO;
import com.feetfit.server.web.dto.report.ReportResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
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
            summary = "무지외반 분석 결과 저장",
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
            summary = "무지외반 분석 결과 조회",
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
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(reportQueryService.getHalluxValgusAnalysis(userId, date));
    }

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

    private static final String HALLUX_VALGUS_NOT_FOUND_RESPONSE = """
            {
              "isSuccess": false,
              "code": "REPORT4001",
              "message": "리포트를 찾을 수 없습니다.",
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