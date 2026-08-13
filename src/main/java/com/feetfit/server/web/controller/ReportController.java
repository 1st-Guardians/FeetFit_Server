package com.feetfit.server.web.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feetfit.server.apiPayload.ApiResponse;
import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.GeneralException;
import com.feetfit.server.jwt.FindLoginUser;
import com.feetfit.server.service.ReportService.ReportCommandService;
import com.feetfit.server.service.ReportService.ReportQueryService;
import com.feetfit.server.web.dto.report.ReportRequestDTO;
import com.feetfit.server.web.dto.report.ReportResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Tag(name = "Report", description = "리포트 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportCommandService reportCommandService;
    private final ReportQueryService reportQueryService;
    private final FindLoginUser findLoginUser;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @PostMapping("/hallux-valgus")
    @Operation(
            summary = "무지외반 분석 결과 저장 [민지]",
            description = """
                AI 분석 결과로 받은 왼발/오른발 무지외반 데이터를 저장합니다.
                Authorization 헤더에 Bearer accessToken이 필요합니다.
                - request 파트에는 무지외반 분석 JSON 데이터를 문자열로 넣습니다.
                - leftFootImage 파트에는 왼발 키포인트+선분 추출 이미지를 넣습니다.
                - rightFootImage 파트에는 오른발 키포인트+선분 추출 이미지를 넣습니다.
                - 각 발의 각도(HVA) 기반으로 분석 텍스트를 서버에서 자동 생성합니다.
                - riskScore는 max(0, 100 - 2.5 × HVA) 공식으로 서버에서 계산합니다.
                - 같은 측정 세션 ID에 이미 저장된 데이터가 있으면 덮어씁니다 (UPDATE).
                - 같은 측정 세션 ID에 저장된 데이터가 없으면 새로 저장합니다 (INSERT).
                - 측정 세션의 status가 COMPLETED인 경우에만 저장됩니다.
                - 본인의 측정 세션 ID만 사용 가능합니다.
                """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(implementation = ReportRequestDTO.SaveHalluxValgusMultipartDTO.class)
                    )
            )
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "무지외반 분석 결과 저장 성공",
                    content = @Content(examples = @ExampleObject(value = SAVE_HALLUX_VALGUS_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "필수값 누락, 이미지 파일 누락, 잘못된 JSON, 완료되지 않은 측정 세션",
                    content = @Content(examples = {
                            @ExampleObject(name = "유효성 검사 실패", value = VALIDATION_ERROR_RESPONSE),
                            @ExampleObject(name = "이미지 파일 누락", value = IMAGE_REQUIRED_RESPONSE),
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
                    description = "AWS CLI 미설치, S3 권한 없음, S3 업로드 실패 또는 서버 내부 오류",
                    content = @Content(examples = {
                            @ExampleObject(name = "S3 업로드 실패", value = S3_UPLOAD_ERROR_RESPONSE),
                            @ExampleObject(name = "서버 내부 오류", value = INTERNAL_SERVER_ERROR_RESPONSE)
                    })
            )
    })
    public ApiResponse<ReportResponseDTO.SaveHalluxValgusResultDTO> saveHalluxValgusAnalysis(
            @Parameter(description = "무지외반 분석 JSON 문자열 파트", required = true)
            @RequestPart("request") String requestJson,
            @Parameter(description = "왼발 키포인트+선분 추출 이미지 파일", required = true)
            @RequestPart("leftFootImage") MultipartFile leftFootImage,
            @Parameter(description = "오른발 키포인트+선분 추출 이미지 파일", required = true)
            @RequestPart("rightFootImage") MultipartFile rightFootImage
    ) {
        Long userId = findLoginUser.getCurrentUserId();
        ReportRequestDTO.SaveHalluxValgusDTO request = parseHalluxValgusRequest(requestJson);
        return ApiResponse.onSuccess(reportCommandService.saveHalluxValgusAnalysis(
                userId, request, leftFootImage, rightFootImage));
    }

    private ReportRequestDTO.SaveHalluxValgusDTO parseHalluxValgusRequest(String requestJson) {
        if (requestJson == null || requestJson.isBlank()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "request 파트는 필수입니다.");
        }

        ReportRequestDTO.SaveHalluxValgusDTO request;
        try {
            request = objectMapper.readValue(requestJson, ReportRequestDTO.SaveHalluxValgusDTO.class);
        } catch (JsonProcessingException e) {
            throw new GeneralException(
                    ErrorStatus._BAD_REQUEST,
                    "요청 본문 형식이 잘못되었습니다."
            );
        }

        Set<ConstraintViolation<ReportRequestDTO.SaveHalluxValgusDTO>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            Map<String, String> errors = new LinkedHashMap<>();
            violations.forEach(violation -> errors.put(
                    violation.getPropertyPath().toString(),
                    violation.getMessage()
            ));
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "잘못된 요청입니다.", errors);
        }

        return request;
    }

    @PostMapping("/tina-pedis")
    @Operation(
            summary = "무좀 분석 결과 저장 [은서]",
            description = """
                    AI 분석 결과로 받은 무좀 분석 데이터와 실제 발 이미지를 저장합니다.
                    Authorization 헤더에 Bearer accessToken이 필요합니다.
                    - request 파트에는 무좀 분석 JSON 데이터를 문자열로 넣습니다.
                    - suspiciousAreaMapImage 파트에는 의심 부위 표시 이미지를 넣습니다.
                    - originalFootImage 파트에는 원본 발 이미지를 넣습니다.
                    - recordedAt은 요청으로 받지 않고 서버 저장 시각으로 자동 기록합니다.
                    - 이미지는 EC2 서버에서 AWS CLI를 사용해 S3에 업로드하고, 업로드된 S3 URL을 DB에 저장합니다.
                    - safetyScore 값은 0 이상 100 이하만 허용합니다.
                    - totalScore는 진균 의심 안전 점수 70%, 피부 반응 안전 점수 30%로 계산됩니다.
                    - 오늘 날짜를 제외한 과거 측정 결과 중 가장 최근 결과가 있으면 previousTotalScore, totalScoreDiff를 함께 반환합니다.
                    - 과거 측정 데이터가 없으면 previousTotalScore, totalScoreDiff는 null을 반환합니다.
                    - 같은 측정 세션에 이미 저장된 데이터가 있으면 덮어씁니다 (UPDATE).
                    - 같은 측정 세션에 저장된 데이터가 없으면 새로 저장합니다 (INSERT).
                    - 측정 세션의 status가 COMPLETED인 경우에만 저장됩니다.
                    - 본인의 측정 세션 ID만 사용 가능합니다.
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(implementation = ReportRequestDTO.SaveTinaPedisAnalysisMultipartDTO.class)
                    )
            )
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "무좀 분석 결과 저장 성공",
                    content = @Content(examples = @ExampleObject(value = SAVE_TINA_PEDIS_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "필수값 누락, 이미지 파일 누락, 점수 범위 오류, 잘못된 JSON, 완료되지 않은 측정 세션",
                    content = @Content(examples = {
                            @ExampleObject(name = "유효성 검사 실패", value = TINA_PEDIS_VALIDATION_ERROR_RESPONSE),
                            @ExampleObject(name = "이미지 파일 누락", value = IMAGE_REQUIRED_RESPONSE),
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
                    description = "AWS CLI 미설치, S3 권한 없음, S3 업로드 실패 또는 서버 내부 오류",
                    content = @Content(examples = {
                            @ExampleObject(name = "S3 업로드 실패", value = S3_UPLOAD_ERROR_RESPONSE),
                            @ExampleObject(name = "서버 내부 오류", value = INTERNAL_SERVER_ERROR_RESPONSE)
                    })
            )
    })
    public ApiResponse<ReportResponseDTO.TinaPedisAnalysisResultDTO> saveTinaPedisAnalysis(
            @Parameter(description = "무좀 분석 JSON 문자열 파트", required = true)
            @RequestPart("request") String requestJson,
            @Parameter(description = "의심 부위 표시 이미지 파일", required = true)
            @RequestPart("suspiciousAreaMapImage") MultipartFile suspiciousAreaMapImage,
            @Parameter(description = "원본 발 이미지 파일", required = true)
            @RequestPart("originalFootImage") MultipartFile originalFootImage
    ) {
        Long userId = findLoginUser.getCurrentUserId();
        ReportRequestDTO.SaveTinaPedisAnalysisDTO request = parseTinaPedisRequest(requestJson);
        return ApiResponse.onSuccess(reportCommandService.saveTinaPedisAnalysis(
                userId,
                request,
                suspiciousAreaMapImage,
                originalFootImage
        ));
    }

    private ReportRequestDTO.SaveTinaPedisAnalysisDTO parseTinaPedisRequest(String requestJson) {
        if (requestJson == null || requestJson.isBlank()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "request 파트는 필수입니다.");
        }

        ReportRequestDTO.SaveTinaPedisAnalysisDTO request;
        try {
            request = objectMapper.readValue(requestJson, ReportRequestDTO.SaveTinaPedisAnalysisDTO.class);
        } catch (JsonProcessingException e) {
            throw new GeneralException(
                    ErrorStatus._BAD_REQUEST,
                    "요청 본문 형식이 잘못되었습니다. enum 값은 허용된 대문자 값으로 입력해야 합니다."
            );
        }

        Set<ConstraintViolation<ReportRequestDTO.SaveTinaPedisAnalysisDTO>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            Map<String, String> errors = new LinkedHashMap<>();
            violations.forEach(violation -> errors.put(
                    violation.getPropertyPath().toString(),
                    violation.getMessage()
            ));
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "잘못된 요청입니다.", errors);
        }

        return request;
    }

    private static final String SAVE_HALLUX_VALGUS_SUCCESS_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "id": 1,
                "measurementSessionId": 1,
                "leftToeAngleDegree": 23.5,
                "leftAnalysisText": "엄지발가락이 두 번째 발가락 쪽으로 기울어진 각도(HVA)가 23.5°로 측정되었습니다. 변형이 진행된 범위(20~40°)에 해당합니다.",
                "leftImageUrl": "https://project5-42-oregon-feetfit-s3.s3.us-west-2.amazonaws.com/hallux-valgus-left/7f6a8f5e-8b9a-4b12-9f89-4ff7ad2e3a20.png",
                "rightToeAngleDegree": 15.2,
                "rightAnalysisText": "엄지발가락이 두 번째 발가락 쪽으로 기울어진 각도(HVA)가 15.2°로 측정되었습니다. 경미한 변형 범위(15~20°)에 해당합니다.",
                "rightImageUrl": "https://project5-42-oregon-feetfit-s3.s3.us-west-2.amazonaws.com/hallux-valgus-right/9d6c9a1f-5b54-41b8-a5dd-34b76dd77f11.png",
                "riskScore": 75.4,
                "scoreAnalysisText": "왼발 중심으로 무지외반 진행 가능성이 있어 관리가 필요합니다.",
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
                "suspiciousAreaMapImageUrl": "https://project5-42-oregon-feetfit-s3.s3.us-west-2.amazonaws.com/tina-pedis-map/7f6a8f5e-8b9a-4b12-9f89-4ff7ad2e3a20.png",
                "originalFootImageUrl": "https://project5-42-oregon-feetfit-s3.s3.us-west-2.amazonaws.com/tina-pedis-original/9d6c9a1f-5b54-41b8-a5dd-34b76dd77f11.png",
                "recordedAt": "2026-05-28T02:55:43",
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
                "totalScoreDescription": "종합 점수 설명은 필수입니다."
              }
            }
            """;

    private static final String IMAGE_REQUIRED_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON400",
              "message": "업로드할 이미지 파일은 필수입니다.",
              "result": null
            }
            """;

    private static final String S3_UPLOAD_ERROR_RESPONSE = """
            {
              "isSuccess": false,
              "code": "COMMON500",
              "message": "S3 이미지 업로드에 실패했습니다.",
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

    @PostMapping("/daily-foot-analysis/condition")
    @Operation(
            summary = "종합 발 분석 - 오늘의 발 컨디션 저장 [민지]",
            description = """
                오늘의 발 상태 레벨과 코멘트를 저장합니다.
                Authorization 헤더에 Bearer accessToken이 필요합니다.
                - 같은 측정 세션 ID에 이미 저장된 데이터가 있으면 해당 파트만 덮어씁니다 (upsert).
                - 측정 세션의 status가 TRANSFERRING 상태여야 합니다.
                """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    content = @Content(examples = @ExampleObject(value = DAILY_FOOT_ANALYSIS_SUCCESS_RESPONSE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    content = @Content(examples = @ExampleObject(value = MEASUREMENT_FORBIDDEN_RESPONSE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    content = @Content(examples = @ExampleObject(value = MEASUREMENT_NOT_FOUND_RESPONSE)))
    })
    public ApiResponse<ReportResponseDTO.DailyFootAnalysisResultDTO> saveConditionPart(
            @RequestBody @Valid ReportRequestDTO.ConditionPartDTO request) {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(reportCommandService.saveConditionPart(userId, request));
    }

    @PostMapping("/daily-foot-analysis/balance")
    @Operation(
            summary = "종합 발 분석 - 자세 균형 저장 [민지]",
            description = """
                자세 균형 점수와 코멘트를 저장합니다.
                Authorization 헤더에 Bearer accessToken이 필요합니다.
                """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    content = @Content(examples = @ExampleObject(value = DAILY_FOOT_ANALYSIS_SUCCESS_RESPONSE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    content = @Content(examples = @ExampleObject(value = MEASUREMENT_FORBIDDEN_RESPONSE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    content = @Content(examples = @ExampleObject(value = MEASUREMENT_NOT_FOUND_RESPONSE)))
    })
    public ApiResponse<ReportResponseDTO.DailyFootAnalysisResultDTO> saveBalancePart(
            @RequestBody @Valid ReportRequestDTO.BalancePartDTO request) {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(reportCommandService.saveBalancePart(userId, request));
    }

    @PostMapping(value = "/daily-foot-analysis/pressure",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "종합 발 분석 - 압력 분포 저장 [민지]",
            description = """
                좌우 압력 비율과 압력 분포 이미지를 저장합니다.
                Authorization 헤더에 Bearer accessToken이 필요합니다.
                - request 파트: PressurePartDTO JSON (Content-Type: application/json)
                - leftPressureImage, rightPressureImage: 이미지 파일 (S3 업로드)
                """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(implementation = ReportRequestDTO.PressurePartMultipartDTO.class)
                    )
            )
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    content = @Content(examples = @ExampleObject(value = DAILY_FOOT_ANALYSIS_SUCCESS_RESPONSE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    content = @Content(examples = @ExampleObject(value = MEASUREMENT_FORBIDDEN_RESPONSE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    content = @Content(examples = @ExampleObject(value = MEASUREMENT_NOT_FOUND_RESPONSE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500",
                    content = @Content(examples = @ExampleObject(name = "S3 업로드 실패", value = S3_UPLOAD_ERROR_RESPONSE)))
    })
    public ApiResponse<ReportResponseDTO.DailyFootAnalysisResultDTO> savePressurePart(
            @RequestPart("request") String requestJson,
            @RequestPart("leftPressureImage") MultipartFile leftPressureImage,
            @RequestPart("rightPressureImage") MultipartFile rightPressureImage) {
        Long userId = findLoginUser.getCurrentUserId();
        ReportRequestDTO.PressurePartDTO request = parsePressurePartRequest(requestJson);
        return ApiResponse.onSuccess(reportCommandService.savePressurePart(
                userId, request, leftPressureImage, rightPressureImage));
    }

    private ReportRequestDTO.PressurePartDTO parsePressurePartRequest(String requestJson) {
        if (requestJson == null || requestJson.isBlank()) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "request 파트는 필수입니다.");
        }
        ReportRequestDTO.PressurePartDTO request;
        try {
            request = objectMapper.readValue(requestJson, ReportRequestDTO.PressurePartDTO.class);
        } catch (JsonProcessingException e) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "요청 본문 형식이 잘못되었습니다.");
        }
        Set<ConstraintViolation<ReportRequestDTO.PressurePartDTO>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            Map<String, String> errors = new LinkedHashMap<>();
            violations.forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));
            throw new GeneralException(ErrorStatus._BAD_REQUEST, "잘못된 요청입니다.", errors);
        }
        return request;
    }

    @PostMapping("/daily-foot-analysis/metrics")
    @Operation(
            summary = "종합 발 분석 - 발 수치 저장 [민지]",
            description = """
                측정된 발 길이 및 발볼 너비를 저장합니다.
                Authorization 헤더에 Bearer accessToken이 필요합니다.
                """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    content = @Content(examples = @ExampleObject(value = DAILY_FOOT_ANALYSIS_SUCCESS_RESPONSE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    content = @Content(examples = @ExampleObject(value = MEASUREMENT_FORBIDDEN_RESPONSE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    content = @Content(examples = @ExampleObject(value = MEASUREMENT_NOT_FOUND_RESPONSE)))
    })
    public ApiResponse<ReportResponseDTO.DailyFootAnalysisResultDTO> saveMetricsPart(
            @RequestBody @Valid ReportRequestDTO.MetricsPartDTO request) {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(reportCommandService.saveMetricsPart(userId, request));
    }

    @PostMapping("/daily-foot-analysis/environment")
    @Operation(
            summary = "종합 발 분석 - 환경 상태 저장 [민지]",
            description = """
                발 환경의 평균 온도·습도를 저장합니다.
                Authorization 헤더에 Bearer accessToken이 필요합니다.
                """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    content = @Content(examples = @ExampleObject(value = DAILY_FOOT_ANALYSIS_SUCCESS_RESPONSE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    content = @Content(examples = @ExampleObject(value = MEASUREMENT_FORBIDDEN_RESPONSE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    content = @Content(examples = @ExampleObject(value = MEASUREMENT_NOT_FOUND_RESPONSE)))
    })
    public ApiResponse<ReportResponseDTO.DailyFootAnalysisResultDTO> saveEnvironmentPart(
            @RequestBody @Valid ReportRequestDTO.EnvironmentPartDTO request) {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(reportCommandService.saveEnvironmentPart(userId, request));
    }

    @PostMapping("/daily-foot-analysis/care-tips")
    @Operation(
            summary = "종합 발 분석 - 관리팁/발 타입 저장 [민지]",
            description = """
                관리 팁 3개와 발 타입 텍스트를 저장합니다.
                Authorization 헤더에 Bearer accessToken이 필요합니다.
                - careTips는 정확히 3개여야 합니다.
                """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    content = @Content(examples = @ExampleObject(value = DAILY_FOOT_ANALYSIS_SUCCESS_RESPONSE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    content = @Content(examples = @ExampleObject(value = MEASUREMENT_FORBIDDEN_RESPONSE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    content = @Content(examples = @ExampleObject(value = MEASUREMENT_NOT_FOUND_RESPONSE)))
    })
    public ApiResponse<ReportResponseDTO.DailyFootAnalysisResultDTO> saveCareTipsPart(
            @RequestBody @Valid ReportRequestDTO.CareTipsPartDTO request) {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(reportCommandService.saveCareTipsPart(userId, request));
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

    @PostMapping("/summary")
    @Operation(
            summary = "지표별 리포트 저장 [민지]",
            description = """
                분석 완료된 단일 지표의 결과를 저장합니다.
                Authorization 헤더에 Bearer accessToken이 필요합니다.
                - 지표 타입: PRESSURE_BALANCE, HALLUX_VALGUS, ATHLETES_FOOT, SKIN_IRRITATION, FOOT_ENVIRONMENT
                - 같은 측정 세션 + 같은 지표 타입이 이미 저장되어 있으면 덮어씁니다 (upsert).
                - 측정 세션의 status가 TRANSFERRING 상태여야 합니다.
                - 본인의 측정 세션 ID만 사용 가능합니다.
                - advice는 정확히 2개여야 합니다.
                - 5개 지표가 모두 저장 완료되면 totalScore(단순 평균)를 계산하고 발 관리 투두를 매칭합니다.
                - allMetricsComplete: 5개 지표 모두 저장 완료 여부
                - missingMetrics: 아직 저장되지 않은 지표 목록 (완료 시 빈 리스트)
                - totalScore, matchedHealthTypes, matchedTodoCount: 완료 시에만 반환 (미완료 시 null)
                """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "지표 저장 성공",
                    content = @Content(examples = @ExampleObject(value = SAVE_METRIC_RESULT_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "필수값 누락, 전송 중이 아닌 측정 세션",
                    content = @Content(examples = {
                            @ExampleObject(name = "유효성 검사 실패", value = METRIC_RESULT_VALIDATION_ERROR_RESPONSE),
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
    public ApiResponse<ReportResponseDTO.SaveMetricResultResultDTO> saveMetricResult(
            @RequestBody @Valid ReportRequestDTO.SaveMetricResultDTO request
    ) {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(reportCommandService.saveMetricResult(userId, request));
    }

    @GetMapping("/summary")
    @Operation(
            summary = "요약 페이지 조회 [민지]",
            description = """
                오늘 날짜 기준으로 발 종합 점수, 지표별 점수, 1년간 변화 추이를 조회합니다.
                Authorization 헤더에 Bearer accessToken이 필요합니다.
                - 지표 타입: PRESSURE_BALANCE(압력 균형), HALLUX_VALGUS(무지외반), ATHLETES_FOOT(무좀), SKIN_IRRITATION(피부 자극도), FOOT_ENVIRONMENT(환경 상태)
                - monthlyScores: 최근 12개월 월별 종합 점수 평균 (5개 지표 모두 있는 날만 포함)
                - totalScore: 5개 지표 단순 평균으로 백엔드에서 계산
                - advice: 각 지표별 설명 2개
                - 오늘 저장된 데이터가 없으면 404를 반환합니다.
                - 5개 지표가 모두 저장되지 않은 경우 400(REPORT4002)을 반환하며, 누락된 지표 목록이 메시지에 포함됩니다.
                """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "요약 페이지 조회 성공",
                    content = @Content(examples = @ExampleObject(value = REPORT_SUMMARY_SUCCESS_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authorization 헤더 누락 또는 유효하지 않은 토큰",
                    content = @Content(examples = @ExampleObject(value = UNAUTHORIZED_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "오늘 저장된 리포트 없음",
                    content = @Content(examples = @ExampleObject(value = REPORT_NOT_FOUND_RESPONSE))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_RESPONSE))
            )
    })
    public ApiResponse<ReportResponseDTO.ReportSummaryResultDTO> getReportSummary() {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(reportQueryService.getReportSummary(userId));
    }

    @GetMapping("/measured-dates")
    @Operation(
            summary = "측정 날짜 리스트 조회 [민지]",
            description = """
                년월 기준으로 해당 월의 측정 날짜 리스트를 조회합니다.
                Authorization 헤더에 Bearer accessToken이 필요합니다.
                - year: 조회할 연도
                - month: 조회할 월 (1~12)
                - COMPLETED 상태인 측정 세션만 조회합니다.
                - 측정하지 않은 날은 포함되지 않습니다.
                """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "측정 날짜 리스트 조회 성공",
                    content = @Content(examples = @ExampleObject(value = MEASURED_DATES_SUCCESS_RESPONSE))
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
    public ApiResponse<ReportResponseDTO.MeasuredDateListResultDTO> getMeasuredDates(
            @Parameter(description = "조회 연도", example = "2026")
            @RequestParam int year,
            @Parameter(description = "조회 월 (1~12)", example = "5")
            @RequestParam @Min(value = 1, message = "month는 1 이상이어야 합니다.")
            @Max(value = 12, message = "month는 12 이하이어야 합니다.") int month
    ) {
        Long userId = findLoginUser.getCurrentUserId();
        return ApiResponse.onSuccess(reportQueryService.getMeasuredDates(userId, year, month));
    }

    private static final String MEASURED_DATES_SUCCESS_RESPONSE = """
        {
          "isSuccess": true,
          "code": "COMMON200",
          "message": "성공입니다.",
          "result": {
            "measuredDates": [
              "2026-05-01",
              "2026-05-15",
              "2026-05-30",
              "2026-05-31"
            ]
          }
        }
        """;

    private static final String SAVE_METRIC_RESULT_SUCCESS_RESPONSE = """
        {
          "isSuccess": true,
          "code": "COMMON200",
          "message": "성공입니다.",
          "result": {
            "reportId": 1,
            "measurementSessionId": 1,
            "savedMetricType": "HALLUX_VALGUS",
            "score": 72.0,
            "status": "ATTENTION_NEEDED",
            "advice": [
              "엄지발가락이 안쪽으로 약간 기울어지는 변화가 관찰되며, 발 앞쪽 부담에 주의가 필요합니다.",
              "앞쪽 발에 부담이 커지지 않도록 편한 신발을 착용하고, 발가락 스트레칭과 상태 관리를 꾸준히 해주세요."
            ],
            "allMetricsComplete": false,
            "totalScore": null,
            "missingMetrics": ["ATHLETES_FOOT", "FOOT_ENVIRONMENT", "SKIN_IRRITATION", "PRESSURE_BALANCE"],
            "matchedHealthTypes": null,
            "matchedTodoCount": null,
            "matchedArticleHealthTypes": null,
            "matchedArticleCount": null,
            "createdAt": "2026-05-20T09:00:00",
            "updatedAt": "2026-05-20T09:00:00"
          }
        }
        """;

    private static final String METRIC_RESULT_VALIDATION_ERROR_RESPONSE = """
        {
          "isSuccess": false,
          "code": "COMMON400",
          "message": "잘못된 요청입니다.",
          "result": {
            "measurementSessionId": "측정 세션 ID는 필수입니다.",
            "metricType": "지표 타입은 필수입니다.",
            "score": "점수는 필수입니다.",
            "advice": "어드바이스는 2개여야 합니다."
          }
        }
        """;

    private static final String REPORT_SUMMARY_SUCCESS_RESPONSE = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "result": {
                "totalScore": 83,
                "metricScores": [
                  {
                    "metricType": "PRESSURE_BALANCE",
                    "score": 85.0,
                    "status": "VERY_GOOD",
                    "advice": [
                      "좌우 발의 압력 분포에 다소 차이가 나타나고 있습니다.",
                      "보행 시 체중을 양쪽 발에 고르게 분산하는 습관을 의식하는 것이 필요합니다."
                    ]
                  },
                  {
                    "metricType": "HALLUX_VALGUS",
                    "score": 72.0,
                    "status": "ATTENTION_NEEDED",
                    "advice": [
                      "엄지발가락이 안쪽으로 약간 기울어지는 변화가 관찰되며, 발 앞쪽 부담에 주의가 필요합니다.",
                      "앞쪽 발에 부담이 커지지 않도록 편한 신발을 착용하고, 발가락 스트레칭과 상태 관리를 꾸준히 해주세요."
                    ]
                  },
                  {
                    "metricType": "ATHLETES_FOOT",
                    "score": 90.0,
                    "status": "VERY_GOOD",
                    "advice": [
                      "무좀 의심 징후가 거의 나타나지 않으며, 발 피부 상태가 전반적으로 매우 안정적인 상태입니다.",
                      "현재처럼 발을 청결하고 건조하게 관리하면 무좀 발생 가능성을 낮추는 데 도움이 됩니다."
                    ]
                  },
                  {
                    "metricType": "SKIN_IRRITATION",
                    "score": 88.0,
                    "status": "VERY_GOOD",
                    "advice": [
                      "피부 자극으로 이어질 수 있는 발 피부 변화 영역이 거의 나타나지 않아 전반적으로 매우 안정적인 상태입니다.",
                      "현재처럼 발을 청결하게 유지하고 마찰이 적은 신발을 착용하면 피부 자극을 줄이는 데 도움이 됩니다."
                    ]
                  },
                  {
                    "metricType": "FOOT_ENVIRONMENT",
                    "score": 65.0,
                    "status": "NEED_IMPROVEMENT",
                    "advice": [
                      "발 주변의 온도와 습도가 전반적으로 높게 나타나며, 발 환경 관리 개선이 필요한 상태입니다.",
                      "피부 트러블이나 무좀 위험을 줄이기 위해 발을 청결하고 건조하게 유지하고, 통풍이 잘 되는 신발을 착용해주세요."
                    ]
                  }
                ],
                "monthlyScores": [
                  { "month": 1, "avgScore": 75.0 },
                  { "month": 2, "avgScore": 77.5 },
                  { "month": 3, "avgScore": 78.0 },
                  { "month": 4, "avgScore": 80.0 },
                  { "month": 5, "avgScore": 83.0 }
                ]
              }
            }
            """;

    private static final String REPORT_NOT_FOUND_RESPONSE = """
            {
              "isSuccess": false,
              "code": "REPORT4001",
              "message": "리포트를 찾을 수 없습니다.",
              "result": null
            }
            """;

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
                "measurementSessionId": "측정 세션 ID는 필수입니다."
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
                "measurementSessionId": 1,
                "leftToeAngleDegree": 23.5,
                "leftAnalysisText": "왼발 무지외반 주의 필요",
                "leftImageUrl": "https://example.com/hallux-left.jpg",
                "rightToeAngleDegree": 15.2,
                "rightAnalysisText": "오른발 양호",
                "rightImageUrl": "https://example.com/hallux-right.jpg",
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
