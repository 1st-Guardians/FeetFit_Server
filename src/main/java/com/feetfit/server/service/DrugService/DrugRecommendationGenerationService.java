package com.feetfit.server.service.DrugService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DrugRecommendationGenerationService {
    private final DrugRecommendationStore store;
    private final DrugRecommendationSummaryClient client;

    @Async("drugRecommendationTaskExecutor")
    public void generate(Long reportId) {
        generateNow(reportId);
    }

    @Async("drugRecommendationTaskExecutor")
    public void generateForSession(Long userId, Long measurementSessionId) {
        try {
            var prepared = store.prepare(userId, measurementSessionId);
            generateNow(prepared.reportId());
        } catch (Exception e) {
            log.warn("Drug recommendation preparation failed. measurementSessionId={}", measurementSessionId, e);
            failPendingForSession(userId, measurementSessionId);
        }
    }

    public void failPendingForSession(Long userId, Long measurementSessionId) {
        try {
            store.failPendingForSession(userId, measurementSessionId);
        } catch (Exception e) {
            log.error("Drug recommendation preparation failure could not be recorded. measurementSessionId={}",
                    measurementSessionId, e);
        }
    }

    public void generateNow(Long reportId) {
        var prepared = store.readPrepared(reportId);
        if (!store.claim(prepared)) {
            return;
        }
        try {
            if (!prepared.model().equals(client.model())) {
                throw new IllegalStateException("Prepared recommendation model no longer matches current model");
            }
            String summary = client.generate(prepared.basis());
            store.complete(prepared, summary);
        } catch (Exception e) {
            store.fail(prepared);
            log.warn("Drug recommendation GPT summary failed; stored rule-based summary remains available. reportId={}",
                    reportId, e);
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
