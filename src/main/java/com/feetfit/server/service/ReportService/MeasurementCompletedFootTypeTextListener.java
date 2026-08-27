package com.feetfit.server.service.ReportService;

import com.feetfit.server.event.MeasurementCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class MeasurementCompletedFootTypeTextListener {

    private final FootTypeTextAutomationDispatcher dispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMeasurementCompleted(MeasurementCompletedEvent event) {
        try {
            dispatcher.dispatch(event.userId(), event.measurementSessionId());
        } catch (RuntimeException exception) {
            log.error(
                    "Automatic foot type text dispatch rejected. measurementSessionId={}",
                    event.measurementSessionId(),
                    exception);
        }
    }
}
