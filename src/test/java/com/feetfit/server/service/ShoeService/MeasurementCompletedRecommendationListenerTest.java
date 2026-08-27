package com.feetfit.server.service.ShoeService;

import com.feetfit.server.event.MeasurementCompletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class MeasurementCompletedRecommendationListenerTest {

    @Mock ShoeRecommendationAutomationDispatcher dispatcher;

    @Test
    void executorRejectionNeverEscapesAfterCommitListener() {
        MeasurementCompletedRecommendationListener listener =
                new MeasurementCompletedRecommendationListener(dispatcher);
        doThrow(new org.springframework.core.task.TaskRejectedException("queue full"))
                .when(dispatcher).dispatch(7L, 21L);

        assertThatCode(() -> listener.onMeasurementCompleted(
                new MeasurementCompletedEvent(21L, 7L))).doesNotThrowAnyException();
    }

    @Test
    void listenerRunsOnlyAfterTheMeasurementTransactionCommits() throws Exception {
        TransactionalEventListener annotation =
                MeasurementCompletedRecommendationListener.class
                        .getMethod("onMeasurementCompleted", MeasurementCompletedEvent.class)
                        .getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(annotation.fallbackExecution()).isFalse();
    }
}
