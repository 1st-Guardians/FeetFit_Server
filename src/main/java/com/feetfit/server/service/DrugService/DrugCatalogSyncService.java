package com.feetfit.server.service.DrugService;

import com.feetfit.server.domain.enums.DrugCategory;
import com.feetfit.server.web.dto.drug.DrugResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class DrugCatalogSyncService {
    public record SyncResult(int savedProducts, int failedCategories, int failedDetails, boolean skipped) {
    }

    private static final int PAGE_SIZE = 100;
    private final DrugDataClient client;
    private final DrugCatalogStore store;
    private final AtomicBoolean running = new AtomicBoolean();

    public SyncResult synchronize() {
        if (!running.compareAndSet(false, true)) {
            return new SyncResult(0, 0, 0, true);
        }
        var executor = Executors.newFixedThreadPool(4);
        try {
            store.refreshEligibility();
            Set<String> savedIds = new HashSet<>();
            Set<String> enrichedIds = new HashSet<>();
            int failedCategories = 0;
            int failedDetails = 0;
            for (DrugCategory category : DrugCategory.values()) {
                int order = 0;
                List<DrugResponseDTO.DrugItemDTO> onlineProducts = client.getOnlineProducts(category);
                store.saveProducts(category, onlineProducts, 0);
                for (DrugResponseDTO.DrugItemDTO product : onlineProducts) {
                    savedIds.add(product.getId());
                }
                order += onlineProducts.size();
                if (category.isOnline()) {
                    continue;
                }
                try {
                    int page = 0;
                    long processed = 0;
                    DrugDataClient.DrugPage response;
                    do {
                        response = client.fetchDrugPage(category, page, PAGE_SIZE);
                        if (response.items().isEmpty() && processed < response.totalCount()) {
                            throw new IllegalStateException("Drug API returned an empty page before totalCount");
                        }
                        for (DrugResponseDTO.DrugItemDTO product : response.items()) {
                            if (product.getId() == null || !product.getId().matches("^\\d{6,20}$")) {
                                throw new IllegalStateException("Drug API returned an invalid itemSeq");
                            }
                        }
                        store.saveProducts(category, response.items(), order);
                        order += response.items().size();
                        List<CompletableFuture<Integer>> detailTasks = new ArrayList<>();
                        for (DrugResponseDTO.DrugItemDTO product : response.items()) {
                            savedIds.add(product.getId());
                            if (enrichedIds.add(product.getId())) {
                                detailTasks.add(CompletableFuture.supplyAsync(
                                        () -> synchronizeDetails(product.getId()), executor));
                            }
                        }
                        processed += response.items().size();
                        page++;
                        log.info("Drug catalog page stored. category={}, page={}, saved={}, total={}",
                                category.getId(), page, processed, response.totalCount());
                        failedDetails += detailTasks.stream().mapToInt(CompletableFuture::join).sum();
                    } while (processed < response.totalCount());
                } catch (Exception e) {
                    failedCategories++;
                    log.error("Drug catalog sync failed. Stored products are retained. category={}", category.getId(), e);
                }
            }
            SyncResult result = new SyncResult(savedIds.size(), failedCategories, failedDetails, false);
            log.info("Drug catalog sync finished. result={}", result);
            return result;
        } finally {
            executor.shutdownNow();
            running.set(false);
        }
    }

    private int synchronizeDetails(String itemSeq) {
        int failures = 0;
        DrugResponseDTO.PermitInfoDTO permit = null;
        DrugResponseDTO.RecallInfoDTO recall = null;
        boolean permitFetched = false;
        try {
            permit = client.fetchPermitInfo(itemSeq);
            permitFetched = true;
        } catch (Exception e) {
            failures++;
            log.warn("Drug permit sync failed. Previous data is retained. itemSeq={}", itemSeq, e);
        }
        try {
            recall = client.fetchRecallInfo(itemSeq);
            if ("unavailable".equals(recall.getStatus())) {
                failures++;
            }
        } catch (Exception e) {
            failures++;
            log.warn("Drug recall sync failed. Previous data is retained. itemSeq={}", itemSeq, e);
        }
        try {
            store.saveDetails(itemSeq, permitFetched, permit, recall);
        } catch (Exception e) {
            failures++;
            log.warn("Drug detail storage failed. itemSeq={}", itemSeq, e);
        }
        return failures;
    }
}
