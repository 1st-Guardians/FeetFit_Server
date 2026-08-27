package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.event.MeasurementCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class MeasurementCompletedAutomationListener {

    private final MeasurementCompletedAutomationDispatcher dispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMeasurementCompleted(MeasurementCompletedEvent event) {
        try {
            dispatcher.dispatch(event.userId(), event.measurementSessionId());
        } catch (RuntimeException exception) {
            // Scheduling failure must not turn an already committed measurement
            // completion into an HTTP failure. RecommendationRun retry remains available.
            log.error(
                    "Measurement completion automation dispatch rejected. measurementSessionId={}",
                    event.measurementSessionId(),
                    exception);
        }
    }
}
