package com.feetfit.server.service.DrugService;

import com.feetfit.server.event.DrugRecommendationFinishedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;

@Slf4j
@Service
@RequiredArgsConstructor
public class DrugRecommendationGenerationService {
    private final DrugRecommendationStore store;
    private final DrugRecommendationSummaryClient client;
    private final ApplicationEventPublisher events;

    @Async("drugRecommendationTaskExecutor")
    public void generate(Long reportId) {
        generateNow(reportId, true);
    }

    @Async("drugRecommendationTaskExecutor")
    public void generateForSession(Long userId, Long measurementSessionId) {
        try {
            var prepared = store.prepare(userId, measurementSessionId);
            generateNow(prepared.reportId(), true);
        } catch (Exception e) {
            log.warn("Drug recommendation preparation failed. measurementSessionId={}", measurementSessionId, e);
            failPendingForSession(userId, measurementSessionId);
        }
    }

    public void failPendingForSession(Long userId, Long measurementSessionId) {
        try {
            var failed = store.failPendingForSession(userId, measurementSessionId);
            if (failed != null) {
                notifyFinished(failed, false);
            }
        } catch (Exception e) {
            log.error("Drug recommendation preparation failure could not be recorded. measurementSessionId={}",
                    measurementSessionId, e);
        }
    }

    public void generateNow(Long reportId) {
        generateNow(reportId, false);
    }

    private void generateNow(Long reportId, boolean notifyUser) {
        var prepared = store.readPrepared(reportId);
        if (!store.claim(prepared)) {
            return;
        }
        boolean generated;
        boolean changed;
        try {
            if (!prepared.model().equals(client.model())) {
                throw new IllegalStateException("Prepared recommendation model no longer matches current model");
            }
            String summary = client.generate(prepared.basis());
            changed = store.complete(prepared, summary);
            generated = true;
        } catch (Exception e) {
            changed = store.fail(prepared);
            generated = false;
            log.warn("Drug recommendation GPT summary failed; stored rule-based summary remains available. reportId={}",
                    reportId, e);
        }
        if (notifyUser && changed) {
            notifyFinished(prepared, generated);
        }
    }

    public void notifyFinished(DrugRecommendationStore.Prepared prepared, boolean generated) {
        try {
            // Store methods have returned through their transaction proxy, so the summary is committed.
            events.publishEvent(new DrugRecommendationFinishedEvent(prepared.reportId(), prepared.inputHash(), generated));
        } catch (Exception e) {
            log.warn("Drug recommendation socket notification failed. reportId={}", prepared.reportId(), e);
        }
    }

    @Async("drugRecommendationTaskExecutor")
    public void backfillLatestCompletedReports() {
        for (Long reportId : store.latestCompletedReportIds()) {
            try {
                var prepared = store.prepareReport(reportId);
                generateNow(prepared.reportId());
            } catch (Exception e) {
                log.warn("Drug recommendation preparation failed. reportId={}", reportId, e);
            }
        }
    }
}
