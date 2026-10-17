package com.feetfit.server.service.DrugService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "drug.catalog.sync-enabled", havingValue = "true", matchIfMissing = true)
public class DrugCatalogSyncScheduler {
    private final DrugCatalogSyncService syncService;

    @Async("drugCatalogTaskExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void initializeCatalog() {
        synchronizeCatalog();
    }

    @Async("drugCatalogTaskExecutor")
    @Scheduled(cron = "${drug.catalog.sync-cron:0 0 4 * * *}", zone = "Asia/Seoul")
    public void synchronizeCatalog() {
        try {
            syncService.synchronize();
        } catch (Exception e) {
            log.error("Drug catalog synchronization could not finish", e);
        }
    }
}
