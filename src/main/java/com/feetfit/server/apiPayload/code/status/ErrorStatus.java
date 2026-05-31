package com.feetfit.server.apiPayload.code.status;

import com.feetfit.server.apiPayload.code.BaseErrorCode;
import com.feetfit.server.apiPayload.code.ErrorReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorStatus implements BaseErrorCode {

    // Common
    _INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "서버 에러, 관리자에게 문의 바랍니다."),
    _BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON400", "잘못된 요청입니다."),
    _UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON401", "인증이 필요합니다."),
    _FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON403", "금지된 요청입니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER4001", "사용자를 찾을 수 없습니다."),
    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER4002", "이미 존재하는 사용자입니다."),
    USER_WITHDRAWN(HttpStatus.FORBIDDEN, "USER4003", "탈퇴한 사용자입니다."),
    USER_PROFILE_ALREADY_SETUP(HttpStatus.CONFLICT, "USER4004", "이미 기본 정보가 등록된 사용자입니다."),

    // Auth
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH4001", "유효하지 않은 토큰입니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH4002", "토큰이 만료되었습니다."),
    KAKAO_LOGIN_FAILED(HttpStatus.BAD_REQUEST, "AUTH4003", "카카오 로그인에 실패했습니다."),
    APPLE_LOGIN_FAILED(HttpStatus.BAD_REQUEST, "AUTH4004", "애플 로그인에 실패했습니다."),

    // Device
    DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "DEVICE4001", "기기를 찾을 수 없습니다."),
    DEVICE_ALREADY_REGISTERED(HttpStatus.CONFLICT, "DEVICE4002", "이미 등록된 기기가 있습니다."),
    DEVICE_FORBIDDEN(HttpStatus.FORBIDDEN, "DEVICE4003", "본인의 기기가 아닙니다."),
    DEVICE_NOT_CONNECTED(HttpStatus.BAD_REQUEST, "DEVICE4004", "기기가 연결되어 있지 않습니다."),

    // Measurement
    MEASUREMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "MEASUREMENT4001", "측정 세션을 찾을 수 없습니다."),
    MEASUREMENT_ALREADY_IN_PROGRESS(HttpStatus.CONFLICT, "MEASUREMENT4002", "이미 측정이 진행 중입니다."),
    MEASUREMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "MEASUREMENT4003", "본인의 측정 세션이 아닙니다."),
    MEASUREMENT_NOT_COMPLETED(HttpStatus.BAD_REQUEST, "MEASUREMENT4004", "완료된 측정 세션이 아닙니다."),
    MEASUREMENT_ANALYSIS_NOT_READY(HttpStatus.BAD_REQUEST, "MEASUREMENT4005", "무지외반 또는 무좀 분석 결과가 아직 저장되지 않았습니다."),
    MEASUREMENT_NOT_TRANSFERRING(HttpStatus.BAD_REQUEST, "MEASUREMENT4006", "데이터 전송 중인 측정 세션이 아닙니다."),
    MEASUREMENT_HARDWARE_REQUEST_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "MEASUREMENT5001", "하드웨어 서버 측정 요청에 실패했습니다."),

    // Report
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "REPORT4001", "리포트를 찾을 수 없습니다."),
    TINA_PEDIS_ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "TINA_PEDIS4001", "무좀 분석 결과를 찾을 수 없습니다."),

    // Stretching Todo
    STRETCHING_TODO_NOT_FOUND(HttpStatus.NOT_FOUND, "STRETCHING_TODO4001", "스트레칭 투두를 찾을 수 없습니다."),

    // Shoe
    SHOE_NOT_FOUND(HttpStatus.NOT_FOUND, "SHOE4001", "신발 정보를 찾을 수 없습니다."),
    SHOE_FIT_SCORE_UNAVAILABLE(HttpStatus.BAD_REQUEST, "SHOE4002", "측정 이력이 없어 발 적합도순 정렬을 사용할 수 없습니다."),

    // Health Article
    ARTICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "ARTICLE4001", "아티클을 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    @Override
    public ErrorReasonDTO getReason() {
        return ErrorReasonDTO.builder()
                .isSuccess(false)
                .code(code)
                .message(message)
                .build();
    }

    @Override
    public ErrorReasonDTO getReasonHttpStatus() {
        return ErrorReasonDTO.builder()
                .httpStatus(httpStatus)
                .isSuccess(false)
                .code(code)
                .message(message)
                .build();
    }
}
