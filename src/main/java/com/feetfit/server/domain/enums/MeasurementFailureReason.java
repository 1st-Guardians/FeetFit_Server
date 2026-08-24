package com.feetfit.server.domain.enums;

public enum MeasurementFailureReason {
    CAMERA_ERROR,
    PRESSURE_SENSOR_ERROR,
    AI_SERVER_ERROR,
    HARDWARE_TIMEOUT,
    NETWORK_ERROR,
    USER_CANCELLED,
    REPORT_SAVE_ERROR,
    UNKNOWN
}
