package com.feetfit.server.service.DrugService;

import com.feetfit.server.event.MeasurementCompletedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DrugRecommendationListenerTest {
    private final DrugRecommendationStore store = mock(DrugRecommendationStore.class);
    private final DrugRecommendationGenerationService generation = mock(DrugRecommendationGenerationService.class);
    private final DrugRecommendationListener listener = new DrugRecommendationListener(store, generation);
    private final MeasurementCompletedEvent event = new MeasurementCompletedEvent(21L, 7L);

    @Test
    void savesOrderingBeforeCommitAndDispatchesGptOnlyAfterCommit() throws Exception {
        listener.prepare(event);
        verify(store).prepare(7L, 21L);
        verifyNoInteractions(generation);
        listener.generate(event);
        verify(generation).generateForSession(7L, 21L);
        assertThat(phase("prepare")).isEqualTo(TransactionPhase.BEFORE_COMMIT);
        assertThat(phase("generate")).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    void rejectedBackgroundJobDoesNotFailCommittedReport() {
        doThrow(new org.springframework.core.task.TaskRejectedException("full"))
                .when(generation).generateForSession(7L, 21L);
        assertThatCode(() -> listener.generate(event)).doesNotThrowAnyException();
    }

    private TransactionPhase phase(String method) throws Exception {
        var annotation = DrugRecommendationListener.class.getMethod(method, MeasurementCompletedEvent.class)
                .getAnnotation(TransactionalEventListener.class);
        assertThat(annotation.fallbackExecution()).isFalse();
        return annotation.phase();
    }
}
