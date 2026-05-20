package com.feetfit.server.apiPayload;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.feetfit.server.apiPayload.code.BaseCode;
import com.feetfit.server.apiPayload.code.ReasonDTO;
import com.feetfit.server.apiPayload.code.status.SuccessStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@JsonPropertyOrder({"isSuccess", "code", "message", "result"})
@Schema(description = "공통 API 응답 형식")
public class ApiResponse<T> {

    @JsonProperty("isSuccess")
    @Schema(description = "요청 성공 여부", example = "true")
    private final Boolean isSuccess;

    @Schema(description = "응답 코드", example = "COMMON200")
    private final String code;

    @Schema(description = "응답 메시지", example = "성공입니다.")
    private final String message;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "API별 응답 데이터")
    private T result;

    public static <T> ApiResponse<T> onSuccess(T result) {
        return new ApiResponse<>(true, SuccessStatus._OK.getCode(), SuccessStatus._OK.getMessage(), result);
    }

    public static <T> ApiResponse<T> of(BaseCode code, T result) {
        ReasonDTO reasonDTO = code.getReasonHttpStatus();
        return new ApiResponse<>(reasonDTO.isSuccess(), reasonDTO.getCode(), reasonDTO.getMessage(), result);
    }

    public static <T> ApiResponse<T> onFailure(String code, String message, T data) {
        return new ApiResponse<>(false, code, message, data);
    }
}
