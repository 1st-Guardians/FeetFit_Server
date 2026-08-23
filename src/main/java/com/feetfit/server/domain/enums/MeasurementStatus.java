package com.feetfit.server.domain.enums;

public enum MeasurementStatus {
    WAITING_FOR_PHOTO,
    READY_FOR_PHOTO,
    CAPTURING_PHOTO,
    WAITING_FOR_PRESSURE,
    READY_FOR_PRESSURE,
    MEASURING_PRESSURE,
    COMPLETED,
    FAILED,

    PENDING,
    MEASURING,
    TRANSFERRING
}
