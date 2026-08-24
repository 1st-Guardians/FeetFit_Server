package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.domain.enums.MeasurementFailureReason;

public final class MeasurementFailureMessageResolver {

    private MeasurementFailureMessageResolver() {
    }

    public static String resolve(MeasurementFailureReason failureReason) {
        if (failureReason == null) {
            return null;
        }

        return switch (failureReason) {
            case CAMERA_ERROR -> "사진 촬영 중 문제가 발생했습니다. 다시 촬영해 주세요.";
            case PRESSURE_SENSOR_ERROR -> "압력 센서 측정 중 문제가 발생했습니다. 다시 측정해 주세요.";
            case AI_SERVER_ERROR -> "분석 서버 처리 중 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.";
            case HARDWARE_TIMEOUT -> "하드웨어 응답 시간이 초과되었습니다. 기기 연결 상태를 확인해 주세요.";
            case NETWORK_ERROR -> "네트워크 연결이 불안정합니다. 연결 상태를 확인해 주세요.";
            case USER_CANCELLED -> "측정이 취소되었습니다.";
            case REPORT_SAVE_ERROR -> "분석 결과 저장 중 문제가 발생했습니다. 다시 시도해 주세요.";
            case UNKNOWN -> "알 수 없는 문제가 발생했습니다. 다시 시도해 주세요.";
        };
    }
}
