package com.feetfit.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncConfigTest {

    private final AsyncConfig config = new AsyncConfig();

    @Test
    void measurementCompletionExecutorSerializesTheOrderedWorkflow() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor)
                config.measurementCompletionAutomationTaskExecutor();
        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(1);
            assertThat(executor.getMaxPoolSize()).isEqualTo(1);
            assertThat(executor.getQueueCapacity()).isEqualTo(100);
            assertThat(executor.getThreadNamePrefix())
                    .isEqualTo("measurement-completion-automation-");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void summariesUseAnIndependentExecutor() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor)
                config.shoeSummaryTaskExecutor();
        try {
            assertThat(executor.getThreadNamePrefix()).isEqualTo("shoe-summary-");
            assertThat(executor.getMaxPoolSize()).isEqualTo(2);
        } finally {
            executor.shutdown();
        }
    }
}
