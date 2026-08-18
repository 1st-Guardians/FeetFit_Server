package com.feetfit.server.domain.enums;

public enum MeasurementStatus {
    WAITING_FOR_PHOTO,
    CAPTURING_PHOTO,
    WAITING_FOR_PRESSURE,
    MEASURING_PRESSURE,
    PROCESSING,
    COMPLETED,
    FAILED,

    PENDING,
    MEASURING,
    TRANSFERRING
}
