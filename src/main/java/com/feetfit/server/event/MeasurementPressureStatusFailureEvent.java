package com.feetfit.server.event;

import com.feetfit.server.domain.enums.MeasurementStatus;

public record MeasurementPressureStatusFailureEvent(Long userId, Long measurementSessionId,
                                                     Integer photoCaptureAttempt, int pressureMeasurementAttempt,
                                                     MeasurementStatus requestedStatus) {
}
