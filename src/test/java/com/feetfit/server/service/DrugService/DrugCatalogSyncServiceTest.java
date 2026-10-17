package com.feetfit.server.service.DrugService;

import com.feetfit.server.domain.enums.DrugCategory;
import com.feetfit.server.web.dto.drug.DrugResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DrugCatalogSyncServiceTest {
    @Mock DrugDataClient client;
    @Mock DrugCatalogStore store;
    @InjectMocks DrugCatalogSyncService sync;

    @BeforeEach
    void defaults() {
        lenient().when(client.getOnlineProducts(any())).thenReturn(List.of());
        lenient().when(client.fetchDrugPage(any(), anyInt(), anyInt()))
                .thenReturn(new DrugDataClient.DrugPage(List.of(), 0));
        lenient().when(client.fetchRecallInfo(anyString())).thenReturn(
                DrugResponseDTO.RecallInfoDTO.builder().status("clear").records(List.of()).build());
    }

    @Test
    void importsAllPagesAndStoresSharedDrugOnceWithBothCategoryLinks() {
        var first = item("199400001");
        var second = item("199400002");
        var third = item("199400003");
        when(client.fetchDrugPage(DrugCategory.ATHLETES_FOOT, 0, 100))
                .thenReturn(new DrugDataClient.DrugPage(List.of(first, second), 3));
        when(client.fetchDrugPage(DrugCategory.ATHLETES_FOOT, 1, 100))
                .thenReturn(new DrugDataClient.DrugPage(List.of(third), 3));
        when(client.fetchDrugPage(DrugCategory.WOUND_CARE, 0, 100))
                .thenReturn(new DrugDataClient.DrugPage(List.of(first), 1));

        var result = sync.synchronize();

        assertThat(result.savedProducts()).isEqualTo(3);
        assertThat(result.failedCategories()).isZero();
        assertThat(result.failedDetails()).isZero();
        verify(store).saveProducts(DrugCategory.ATHLETES_FOOT, List.of(third), 2);
        verify(store).saveProducts(DrugCategory.WOUND_CARE, List.of(first), 0);
        verify(client, times(1)).fetchPermitInfo("199400001");
        verify(client, times(1)).fetchRecallInfo("199400001");
    }

    @Test
    void failedCategoryDoesNotPreventOnlineOrOtherCategoryImports() {
        var online = item("bunion-day").toBuilder().online(true).build();
        var wound = item("199400001");
        when(client.getOnlineProducts(DrugCategory.BUNION_BAND)).thenReturn(List.of(online));
        when(client.fetchDrugPage(DrugCategory.ATHLETES_FOOT, 0, 100))
                .thenThrow(new IllegalStateException("Upstream unavailable"));
        when(client.fetchDrugPage(DrugCategory.WOUND_CARE, 0, 100))
                .thenReturn(new DrugDataClient.DrugPage(List.of(wound), 1));

        var result = sync.synchronize();

        assertThat(result.savedProducts()).isEqualTo(2);
        assertThat(result.failedCategories()).isOne();
        verify(store).saveProducts(DrugCategory.BUNION_BAND, List.of(online), 0);
        verify(store).saveProducts(DrugCategory.WOUND_CARE, List.of(wound), 0);
    }

    @Test
    void failedPermitFetchPreservesPreviouslyStoredPermitAndStillSavesBaseInformation() {
        var product = item("199400001");
        when(client.fetchDrugPage(DrugCategory.ATHLETES_FOOT, 0, 100))
                .thenReturn(new DrugDataClient.DrugPage(List.of(product), 1));
        when(client.fetchPermitInfo(product.getId())).thenThrow(new IllegalStateException("Timeout"));

        var result = sync.synchronize();

        assertThat(result.failedDetails()).isOne();
        verify(store).saveProducts(DrugCategory.ATHLETES_FOOT, List.of(product), 0);
        verify(store).saveDetails(eq(product.getId()), eq(false), isNull(), any());
    }

    @Test
    void unexpectedlyEmptyPageStopsImportAndAllowsNextRun() {
        when(client.fetchDrugPage(DrugCategory.ATHLETES_FOOT, 0, 100))
                .thenReturn(new DrugDataClient.DrugPage(List.of(), 3));
        assertThat(sync.synchronize().failedCategories()).isOne();
        assertThat(sync.synchronize().skipped()).isFalse();
        verify(client, never()).fetchDrugPage(eq(DrugCategory.ATHLETES_FOOT), eq(1), anyInt());
    }

    @Test
    void overlappingSyncIsSkipped() {
        var online = item("bunion-day");
        when(client.getOnlineProducts(DrugCategory.BUNION_BAND)).thenAnswer(invocation -> {
            assertThat(sync.synchronize().skipped()).isTrue();
            return List.of(online);
        });
        assertThat(sync.synchronize().skipped()).isFalse();
        verify(store, times(1)).saveProducts(DrugCategory.BUNION_BAND, List.of(online), 0);
    }

    @Test
    void detailImportsRunWithBoundedConcurrencyAndFinishBeforeReturning() {
        var products = IntStream.range(0, 8).mapToObj(i -> item(Integer.toString(199400000 + i))).toList();
        when(client.fetchDrugPage(DrugCategory.ATHLETES_FOOT, 0, 100))
                .thenReturn(new DrugDataClient.DrugPage(products, products.size()));
        var barrier = new CyclicBarrier(4);
        var active = new AtomicInteger();
        var peak = new AtomicInteger();
        when(client.fetchPermitInfo(anyString())).thenAnswer(invocation -> {
            peak.accumulateAndGet(active.incrementAndGet(), Math::max);
            try {
                barrier.await(5, TimeUnit.SECONDS);
                return null;
            } finally {
                active.decrementAndGet();
            }
        });

        assertThat(sync.synchronize().failedDetails()).isZero();
        assertThat(peak.get()).isEqualTo(4);
        assertThat(active.get()).isZero();
        verify(store, times(8)).saveDetails(anyString(), eq(true), isNull(), any());
    }

    private DrugResponseDTO.DrugItemDTO item(String id) {
        return DrugResponseDTO.DrugItemDTO.builder().id(id).name(id).online(false).build();
    }
}
