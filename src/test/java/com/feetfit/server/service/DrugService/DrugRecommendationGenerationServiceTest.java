package com.feetfit.server.service.DrugService;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class DrugRecommendationGenerationServiceTest {
    private final DrugRecommendationStore store = mock(DrugRecommendationStore.class);
    private final DrugRecommendationSummaryClient client = mock(DrugRecommendationSummaryClient.class);
    private final DrugRecommendationGenerationService service = new DrugRecommendationGenerationService(store, client);
    private final DrugRecommendationStore.Prepared prepared = new DrugRecommendationStore.Prepared(
            1L, "input-hash", DrugRecommendationSummaryClient.DEFAULT_MODEL,
            new DrugRecommendationPolicy().rank(80f, 35f, 18f, 12f));

    @Test
    void generatesOnlyAfterClaimAndPersistsResult() throws Exception {
        when(store.readPrepared(1L)).thenReturn(prepared);
        when(store.claim(prepared)).thenReturn(true);
        when(client.model()).thenReturn(prepared.model());
        when(client.generate(prepared.basis())).thenReturn("Generated summary");

        service.generate(1L);

        var order = inOrder(store, client);
        order.verify(store).readPrepared(1L);
        order.verify(store).claim(prepared);
        order.verify(client).model();
        order.verify(client).generate(prepared.basis());
        order.verify(store).complete(prepared, "Generated summary");
        verify(store, never()).fail(any());
    }

    @Test
    void duplicateOrCompletedWorkDoesNotCallGpt() {
        when(store.readPrepared(1L)).thenReturn(prepared);
        when(store.claim(prepared)).thenReturn(false);

        service.generateNow(1L);

        verifyNoInteractions(client);
    }

    @Test
    void apiFailureLeavesFallbackAndMarksGenerationFailed() throws Exception {
        when(store.readPrepared(1L)).thenReturn(prepared);
        when(store.claim(prepared)).thenReturn(true);
        when(client.model()).thenReturn(prepared.model());
        when(client.generate(prepared.basis())).thenThrow(new IllegalStateException("unavailable"));

        service.generate(1L);

        verify(store).fail(prepared);
        verify(store, never()).complete(any(), anyString());
    }

    @Test
    void preparationFailureDoesNotStartGpt() {
        when(store.prepare(7L, 21L)).thenThrow(new IllegalStateException("Report unavailable"));

        service.generateForSession(7L, 21L);

        verify(store).failPendingForSession(7L, 21L);
        verify(store, never()).readPrepared(anyLong());
        verifyNoInteractions(client);
    }

    @Test
    void sessionGenerationPreparesOrderingBeforeStartingGpt() throws Exception {
        when(store.prepare(7L, 21L)).thenReturn(prepared);
        when(store.readPrepared(1L)).thenReturn(prepared);
        when(store.claim(prepared)).thenReturn(true);
        when(client.model()).thenReturn(prepared.model());
        when(client.generate(prepared.basis())).thenReturn("Generated summary");
        service.generateForSession(7L, 21L);

        var order = inOrder(store, client);
        order.verify(store).prepare(7L, 21L);
        order.verify(store).readPrepared(1L);
        order.verify(store).claim(prepared);
        order.verify(client).model();
        order.verify(client).generate(prepared.basis());
        order.verify(store).complete(prepared, "Generated summary");
    }

    @Test
    void modelChangeKeepsFallbackWithoutCallingGpt() throws Exception {
        when(store.readPrepared(1L)).thenReturn(prepared);
        when(store.claim(prepared)).thenReturn(true);
        when(client.model()).thenReturn("different-model");

        service.generate(1L);

        verify(store).fail(prepared);
        verify(client, never()).generate(any());
        verify(store, never()).complete(any(), anyString());
    }
}
