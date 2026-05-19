package com.feetfit.server.web.controller;

import com.feetfit.server.apiPayload.ApiResponse;
import com.feetfit.server.jwt.FindLoginUser;
import com.feetfit.server.service.ReportService.ReportCommandService;
import com.feetfit.server.web.dto.report.ReportRequestDTO;
import com.feetfit.server.web.dto.report.ReportResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Report", description = "리포트 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportCommandService reportCommandService;
    private final FindLoginUser findLoginUser;

    @PostMapping("/hallux-valgus")
    @Operation(
            summary = "무지외반 분석 결과 저장",
            description = """
                    AI 분석 결과로 받은 왼발/오른발 무지외반 데이터를 저장합니다.
                    - riskLevel은 LOW, MEDIUM, HIGH만 허용합니다.
                    - analyses 배열에 왼발, 오른발 데이터를 함께 보내주세요.
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
                "createdAt": "2026-05-20T01:55:09"
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

    private static final String UNAUTHORIZED_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON401",
              "message": "인증이 필요합니다.",
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