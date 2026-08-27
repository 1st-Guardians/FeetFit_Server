package com.feetfit.server.service.ReportService;

import com.feetfit.server.event.MeasurementCompletedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MeasurementCompletedFootTypeTextListenerTest {

    @Test
    void dispatchesExactCommittedSessionAndAbsorbsSchedulingFailure() {
        FootTypeTextAutomationDispatcher dispatcher =
                mock(FootTypeTextAutomationDispatcher.class);
        MeasurementCompletedFootTypeTextListener listener =
                new MeasurementCompletedFootTypeTextListener(dispatcher);

        listener.onMeasurementCompleted(new MeasurementCompletedEvent(21L, 7L));
        verify(dispatcher).dispatch(7L, 21L);

        doThrow(new IllegalStateException("queue full"))
                .when(dispatcher).dispatch(7L, 22L);
        assertThatCode(() -> listener.onMeasurementCompleted(
                new MeasurementCompletedEvent(22L, 7L)))
                .doesNotThrowAnyException();
    }

    @Test
    void listenerRunsOnlyAfterCommit() throws NoSuchMethodException {
        TransactionalEventListener annotation =
                MeasurementCompletedFootTypeTextListener.class
                        .getMethod("onMeasurementCompleted", MeasurementCompletedEvent.class)
                        .getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    void dispatcherUsesDedicatedFootTypeExecutor() throws NoSuchMethodException {
        Async annotation = FootTypeTextAutomationDispatcher.class
                .getMethod("dispatch", Long.class, Long.class)
                .getAnnotation(Async.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("footTypeTextTaskExecutor");
    }
}
