package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.MetricType;
import com.feetfit.server.repository.MeasurementSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "measurement.pressure.watchdog-enabled", havingValue = "true", matchIfMissing = true)
public class MeasurementPressureWatchdog {
    private final MeasurementSessionRepository sessions;
    private final MeasurementPressureRecoveryService recovery;

    @Value("${measurement.pressure.start-timeout-seconds:30}")
    private long startTimeoutSeconds = 30;

    @Value("${measurement.pressure.measurement-timeout-seconds:30}")
    private long measurementTimeoutSeconds = 30;

    @Value("${measurement.pressure.analysis-timeout-seconds:600}")
    private long analysisTimeoutSeconds = 600;

    @Scheduled(fixedDelayString = "${measurement.pressure.watchdog-interval-ms:1000}")
    public void recoverStalledSessions() {
        recoverStalledSessions(LocalDateTime.now());
    }

    void recoverStalledSessions(LocalDateTime now) {
        recover(Set.of(MeasurementStatus.READY_FOR_PRESSURE, MeasurementStatus.READY_FOR_PRESSURE_RETRY),
                now.minusSeconds(startTimeoutSeconds));
        recover(Set.of(MeasurementStatus.MEASURING_PRESSURE), now.minusSeconds(measurementTimeoutSeconds));
        recover(Set.of(MeasurementStatus.ANALYZING), now.minusSeconds(analysisTimeoutSeconds));
    }

    private void recover(Set<MeasurementStatus> statuses, LocalDateTime staleBefore) {
        try {
            var candidates = sessions.findPressureTimeoutCandidates(
                    statuses, staleBefore, MetricType.PRESSURE_BALANCE, PageRequest.of(0, 100));
            for (var candidate : candidates) {
                try {
                    // Recheck attempt, state, progress, and saved results under the session lock.
                    recovery.recoverStalledPressure(candidate.getId(), candidate.getPressureRetryCount(),
                            candidate.getStatus(), staleBefore);
                } catch (RuntimeException failure) {
                    log.error("Could not recover stalled pressure session. measurementSessionId={}, pressureMeasurementAttempt={}",
                            candidate.getId(), candidate.getPressureRetryCount(), failure);
                }
            }
        } catch (RuntimeException failure) {
            log.error("Could not check pressure progress. statuses={}", statuses, failure);
        }
    }
}
