package com.feetfit.server.service.DrugService;

import com.feetfit.server.event.MeasurementCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class DrugRecommendationListener {
    private final DrugRecommendationGenerationService generation;

    @Value("${drug.recommendation.backfill-on-startup:true}")
    private boolean backfillOnStartup = true;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void generate(MeasurementCompletedEvent event) {
        try {
            // Completion sockets publish in afterCommit, before this AFTER_COMMIT listener runs.
            generation.generateForSession(event.userId(), event.measurementSessionId());
        } catch (Exception e) {
            log.warn("Drug recommendation dispatch failed. measurementSessionId={}", event.measurementSessionId(), e);
            generation.failPendingForSession(event.userId(), event.measurementSessionId());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void backfill() {
        if (backfillOnStartup) {
            generation.backfillLatestCompletedReports();
        }
    }
}
