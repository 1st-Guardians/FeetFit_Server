package com.feetfit.server.service.DrugService;

import com.feetfit.server.event.DrugRecommendationFinishedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Mockito.*;

class DrugRecommendationGenerationServiceTest {
    private final DrugRecommendationStore store = mock(DrugRecommendationStore.class);
    private final DrugRecommendationSummaryClient client = mock(DrugRecommendationSummaryClient.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final DrugRecommendationGenerationService service = new DrugRecommendationGenerationService(store, client, events);
    private final DrugRecommendationStore.Prepared prepared = new DrugRecommendationStore.Prepared(
            1L, "input-hash", DrugRecommendationSummaryClient.DEFAULT_MODEL,
            new DrugRecommendationPolicy().rank(80f, 35f, 18f, 12f));

    @Test
    void generatesOnlyAfterClaimAndPersistsResult() throws Exception {
        when(store.readPrepared(1L)).thenReturn(prepared);
        when(store.claim(prepared)).thenReturn(true);
        when(client.model()).thenReturn(prepared.model());
        when(client.generate(prepared.basis())).thenReturn("Generated summary");
        when(store.complete(prepared, "Generated summary")).thenReturn(true);

        service.generate(1L);

        var order = inOrder(store, client, events);
        order.verify(store).readPrepared(1L);
        order.verify(store).claim(prepared);
        order.verify(client).model();
        order.verify(client).generate(prepared.basis());
        order.verify(store).complete(prepared, "Generated summary");
        order.verify(events).publishEvent(new DrugRecommendationFinishedEvent(1L, "input-hash", true));
        verify(store, never()).fail(any());
    }

    @Test
    void duplicateOrCompletedWorkDoesNotCallGpt() {
        when(store.readPrepared(1L)).thenReturn(prepared);
        when(store.claim(prepared)).thenReturn(false);

        service.generateNow(1L);

        verifyNoInteractions(client);
        verifyNoInteractions(events);
    }

    @Test
    void apiFailureLeavesFallbackAndMarksGenerationFailed() throws Exception {
        when(store.readPrepared(1L)).thenReturn(prepared);
        when(store.claim(prepared)).thenReturn(true);
        when(client.model()).thenReturn(prepared.model());
        when(client.generate(prepared.basis())).thenThrow(new IllegalStateException("unavailable"));
        when(store.fail(prepared)).thenReturn(true);

        service.generate(1L);

        verify(store).fail(prepared);
        verify(store, never()).complete(any(), anyString());
        verify(events).publishEvent(new DrugRecommendationFinishedEvent(1L, "input-hash", false));
    }

    @Test
    void staleResultDoesNotSendCompletionNotification() throws Exception {
        when(store.readPrepared(1L)).thenReturn(prepared);
        when(store.claim(prepared)).thenReturn(true);
        when(client.model()).thenReturn(prepared.model());
        when(client.generate(prepared.basis())).thenReturn("Stale summary");
        when(store.complete(prepared, "Stale summary")).thenReturn(false);

        service.generate(1L);

        verifyNoInteractions(events);
    }

    @Test
    void backfillDoesNotSendOldMeasurementCompletionToFrontend() throws Exception {
        when(store.readPrepared(1L)).thenReturn(prepared);
        when(store.claim(prepared)).thenReturn(true);
        when(client.model()).thenReturn(prepared.model());
        when(client.generate(prepared.basis())).thenReturn("Generated summary");
        when(store.complete(prepared, "Generated summary")).thenReturn(true);

        service.generateNow(1L);

        verifyNoInteractions(events);
    }

    @Test
    void socketFailureDoesNotMarkStoredGptResultAsFailed() throws Exception {
        when(store.readPrepared(1L)).thenReturn(prepared);
        when(store.claim(prepared)).thenReturn(true);
        when(client.model()).thenReturn(prepared.model());
        when(client.generate(prepared.basis())).thenReturn("Generated summary");
        when(store.complete(prepared, "Generated summary")).thenReturn(true);
        doThrow(new IllegalStateException("Socket unavailable")).when(events).publishEvent(any(DrugRecommendationFinishedEvent.class));

        service.generate(1L);

        verify(store, never()).fail(any());
    }
}
