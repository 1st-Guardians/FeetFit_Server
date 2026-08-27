package com.feetfit.server.service.ShoeService;

import com.feetfit.server.event.MeasurementCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class MeasurementCompletedRecommendationListener {

    private final ShoeRecommendationAutomationDispatcher dispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMeasurementCompleted(MeasurementCompletedEvent event) {
        try {
            dispatcher.dispatch(event.userId(), event.measurementSessionId());
        } catch (RuntimeException exception) {
            // AFTER_COMMIT scheduling failure must never turn a committed measurement
            // completion into an HTTP failure. Operators can use retry-automatic.
            log.error(
                    "Automatic shoe recommendation dispatch rejected. measurementSessionId={}",
                    event.measurementSessionId(), exception);
        }
    }
}
