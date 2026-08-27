package com.feetfit.server.service.MeasurementService;

import com.feetfit.server.event.MeasurementCompletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MeasurementCompletedAutomationListenerTest {

    @Mock MeasurementCompletedAutomationDispatcher dispatcher;

    @Test
    void dispatchesOneWorkflowForTheExactCommittedSession() {
        MeasurementCompletedAutomationListener listener =
                new MeasurementCompletedAutomationListener(dispatcher);

        listener.onMeasurementCompleted(new MeasurementCompletedEvent(21L, 7L));

        verify(dispatcher).dispatch(7L, 21L);
    }

    @Test
    void executorRejectionNeverEscapesAfterCommitListener() {
        MeasurementCompletedAutomationListener listener =
                new MeasurementCompletedAutomationListener(dispatcher);
        doThrow(new org.springframework.core.task.TaskRejectedException("queue full"))
                .when(dispatcher).dispatch(7L, 21L);

        assertThatCode(() -> listener.onMeasurementCompleted(
                new MeasurementCompletedEvent(21L, 7L)))
                .doesNotThrowAnyException();
    }

    @Test
    void listenerRunsOnlyAfterTheMeasurementTransactionCommits() throws Exception {
        TransactionalEventListener annotation =
                MeasurementCompletedAutomationListener.class
                        .getMethod("onMeasurementCompleted", MeasurementCompletedEvent.class)
                        .getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(annotation.fallbackExecution()).isFalse();
    }

    @Test
    void dispatcherUsesTheSingleMeasurementCompletionExecutor() throws Exception {
        Async annotation = MeasurementCompletedAutomationDispatcher.class
                .getMethod("dispatch", Long.class, Long.class)
                .getAnnotation(Async.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value())
                .isEqualTo("measurementCompletionAutomationTaskExecutor");
    }
}
