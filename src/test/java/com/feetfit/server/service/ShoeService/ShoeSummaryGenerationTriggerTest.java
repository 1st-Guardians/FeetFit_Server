package com.feetfit.server.service.ShoeService;

import com.feetfit.server.jwt.TokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoeSummaryGenerationTriggerTest {

    @Mock ShoeRecommendationAiClient aiClient;
    @Mock TokenProvider tokenProvider;

    @Test
    void burstReadsQueueOnlyOneSummaryForTheSameSessionAndShoe() {
        List<Runnable> queued = new ArrayList<>();
        ShoeSummaryGenerationTrigger trigger = trigger(queued::add);
        when(aiClient.isEnabled()).thenReturn(true);
        when(tokenProvider.createAccessToken(7L)).thenReturn("jwt");

        trigger.trigger(7L, 21L, 11L);
        trigger.trigger(7L, 21L, 11L);

        assertThat(queued).hasSize(1);
        queued.remove(0).run();
        verify(aiClient).requestShoeSummary(11L, 21L, "jwt");
    }

    @Test
    void queueRejectionReleasesClaimSoAReplayCanBeScheduled() {
        List<Runnable> queued = new ArrayList<>();
        Executor rejectOnce = new Executor() {
            private boolean first = true;

            @Override
            public void execute(Runnable command) {
                if (first) {
                    first = false;
                    throw new TaskRejectedException("queue full");
                }
                queued.add(command);
            }
        };
        ShoeSummaryGenerationTrigger trigger = trigger(rejectOnce);
        when(aiClient.isEnabled()).thenReturn(true);

        assertThatThrownBy(() -> trigger.trigger(7L, 21L, 11L))
                .isInstanceOf(TaskRejectedException.class);
        trigger.trigger(7L, 21L, 11L);

        assertThat(queued).hasSize(1);
    }

    @Test
    void disabledAutomationDoesNotQueueSummary() {
        List<Runnable> queued = new ArrayList<>();
        ShoeSummaryGenerationTrigger trigger = trigger(queued::add);
        when(aiClient.isEnabled()).thenReturn(false);

        trigger.trigger(7L, 21L, 11L);

        assertThat(queued).isEmpty();
        verifyNoInteractions(tokenProvider);
    }

    private ShoeSummaryGenerationTrigger trigger(Executor executor) {
        return new ShoeSummaryGenerationTrigger(aiClient, tokenProvider, executor);
    }
}
