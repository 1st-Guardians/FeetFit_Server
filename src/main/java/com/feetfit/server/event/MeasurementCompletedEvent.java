package com.feetfit.server.event;

public record MeasurementCompletedEvent(Long measurementSessionId, Long userId) {
}
