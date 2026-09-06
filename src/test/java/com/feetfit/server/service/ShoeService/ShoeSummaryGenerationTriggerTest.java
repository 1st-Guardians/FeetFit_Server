package com.feetfit.server.service.ShoeService;

import com.feetfit.server.jwt.TokenProvider;
import com.feetfit.server.web.dto.shoe.ShoeRequestDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoeSummaryGenerationTriggerTest {

    @Mock ShoeRecommendationAiClient aiClient;
    @Mock TokenProvider tokenProvider;
    @Mock ShoeCommandService shoeCommandService;

    @Test
    void generatedPayloadIsSavedBeforeSuccessReturns() {
        ShoeRequestDTO.SaveShoeSummariesDTO summary = summaryFor(21L);
        when(aiClient.isEnabled()).thenReturn(true);
        when(tokenProvider.createAccessToken(7L)).thenReturn("jwt");
        when(aiClient.generateShoeSummary(11L, 21L, "jwt")).thenReturn(summary);

        boolean generated = trigger().generateNow(7L, 21L, 11L);

        assertThat(generated).isTrue();
        verify(shoeCommandService).saveShoeSummaries(7L, 11L, summary);
    }

    @Test
    void mismatchedMeasurementSessionIsRejectedWithoutSaving() {
        ShoeRequestDTO.SaveShoeSummariesDTO summary = summaryFor(22L);
        when(aiClient.isEnabled()).thenReturn(true);
        when(tokenProvider.createAccessToken(7L)).thenReturn("jwt");
        when(aiClient.generateShoeSummary(11L, 21L, "jwt")).thenReturn(summary);

        assertThat(trigger().generateNow(7L, 21L, 11L)).isFalse();

        verify(shoeCommandService, never()).saveShoeSummaries(7L, 11L, summary);
    }

    @Test
    void disabledAutomationReturnsImmediately() {
        when(aiClient.isEnabled()).thenReturn(false);

        assertThat(trigger().generateNow(7L, 21L, 11L)).isFalse();

        verifyNoInteractions(tokenProvider, shoeCommandService);
    }

    @Test
    void concurrentReadsShareOneGenerationAndOneSave() throws Exception {
        ShoeRequestDTO.SaveShoeSummariesDTO summary = summaryFor(21L);
        CountDownLatch aiEntered = new CountDownLatch(1);
        CountDownLatch releaseAi = new CountDownLatch(1);
        when(aiClient.isEnabled()).thenReturn(true);
        when(tokenProvider.createAccessToken(7L)).thenReturn("jwt");
        when(aiClient.generateShoeSummary(11L, 21L, "jwt")).thenAnswer(invocation -> {
            aiEntered.countDown();
            if (!releaseAi.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test did not release AI response");
            }
            return summary;
        });
        ShoeSummaryGenerationTrigger trigger = trigger();

        CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(
                () -> trigger.generateNow(7L, 21L, 11L));
        assertThat(aiEntered.await(1, TimeUnit.SECONDS)).isTrue();
        AtomicReference<Boolean> secondResult = new AtomicReference<>();
        Thread second = new Thread(
                () -> secondResult.set(trigger.generateNow(7L, 21L, 11L)),
                "summary-test-follower");
        second.start();
        long followerDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (second.getState() != Thread.State.TIMED_WAITING
                && second.getState() != Thread.State.WAITING
                && System.nanoTime() < followerDeadline) {
            Thread.onSpinWait();
        }
        assertThat(second.getState())
                .isIn(Thread.State.TIMED_WAITING, Thread.State.WAITING);
        releaseAi.countDown();

        assertThat(first.get(2, TimeUnit.SECONDS)).isTrue();
        second.join(TimeUnit.SECONDS.toMillis(2));
        assertThat(secondResult.get()).isTrue();
        verify(aiClient, times(1)).generateShoeSummary(11L, 21L, "jwt");
        verify(shoeCommandService, times(1)).saveShoeSummaries(7L, 11L, summary);
    }

    private ShoeSummaryGenerationTrigger trigger() {
        return new ShoeSummaryGenerationTrigger(aiClient, tokenProvider, shoeCommandService);
    }

    private static ShoeRequestDTO.SaveShoeSummariesDTO summaryFor(Long measurementSessionId) {
        ShoeRequestDTO.SaveShoeSummariesDTO summary =
                mock(ShoeRequestDTO.SaveShoeSummariesDTO.class);
        when(summary.getMeasurementSessionId()).thenReturn(measurementSessionId);
        return summary;
    }
}
