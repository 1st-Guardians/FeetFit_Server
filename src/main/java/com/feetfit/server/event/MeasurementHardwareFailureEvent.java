package com.feetfit.server.event;

import com.feetfit.server.domain.enums.MeasurementFailureReason;

public record MeasurementHardwareFailureEvent(Long measurementSessionId, int pressureMeasurementAttempt,
                                               MeasurementFailureReason reason) {
}
