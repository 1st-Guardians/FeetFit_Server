package com.feetfit.server.service.DrugService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.GeneralException;
import com.feetfit.server.domain.enums.DrugCategory;
import com.feetfit.server.repository.drug.DrugProductRepository;
import com.feetfit.server.web.dto.drug.DrugResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Import({JacksonAutoConfiguration.class, DrugCatalogMapper.class, DrugCatalogStore.class, DrugQueryServiceImpl.class})
class DrugCatalogJpaIntegrationTest {
    @Autowired DrugCatalogStore store;
    @Autowired DrugQueryServiceImpl query;
    @Autowired DrugProductRepository products;
    @Autowired TestEntityManager em;

    @Test
    void listsUseDatabasePaginationAcrossOnlineAndOfficialProducts() {
        for (int i = 0; i < 4; i++) {
            store.saveProduct(DrugCategory.WOUND_CARE, item("online-" + i).toBuilder().online(true).build(), i);
        }
        for (int i = 0; i < 26; i++) {
            store.saveProduct(DrugCategory.WOUND_CARE, item(Integer.toString(199400000 + i)), i);
        }
        em.flush();
        em.clear();

        var first = query.getDrugList("wound-care", null, 0, 20);
        var second = query.getDrugList("wound-care", null, 1, 20);

        assertThat(first.getItems()).hasSize(20);
        assertThat(first.getItems().subList(0, 4)).allMatch(DrugResponseDTO.DrugItemDTO::getOnline);
        assertThat(first.getTotalElements()).isEqualTo(30);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.isHasNext()).isTrue();
        assertThat(second.getItems()).hasSize(10);
        assertThat(second.getItems().get(0).getId()).isEqualTo("199400016");
        assertThat(second.isHasNext()).isFalse();
        assertThat(query.getDrugList("wound-care", null, 99, 20).getItems()).isEmpty();
    }

    @Test
    void detailAndListReturnStoredInstructionsPermitRecallAndS3Image() {
        String id = "199400709";
        var drug = item(id);
        var permit = DrugResponseDTO.PermitInfoDTO.builder().itemSeq(id)
                .mainIngredient("Stored ingredient").classification("OTC").status("valid")
                .officialUrl("https://example.com/permit/" + id).build();
        var recall = DrugResponseDTO.RecallInfoDTO.builder().status("alert")
                .checkedAt("2026-10-18T00:00:00Z").sourceUrl("https://example.com/recalls")
                .records(List.of(DrugResponseDTO.RecallRecordDTO.builder()
                        .product("Stored product").reason("Stored recall reason").mandatory(true).build())).build();
        store.saveProduct(DrugCategory.ATHLETES_FOOT, drug, 0);
        store.savePermit(id, permit);
        store.saveRecall(id, recall);
        em.flush();
        em.clear();

        var result = query.getPermitInfo(id);
        assertThat(result.getDrug()).usingRecursiveComparison()
                .isEqualTo(drug.toBuilder().permit(permit).build());
        assertThat(result.getPermit()).usingRecursiveComparison().isEqualTo(permit);
        assertThat(result.getRecall()).usingRecursiveComparison().isEqualTo(recall);
        assertThat(query.getDrugList("athletes-foot", null, 0, 20).getItems().get(0).getPermit())
                .usingRecursiveComparison().isEqualTo(permit);
    }

    @Test
    void refreshingAnExistingProductPreservesDetailsAndBothCategoriesWithoutDuplicates() {
        String id = "199400709";
        store.saveProduct(DrugCategory.ATHLETES_FOOT, item(id), 0);
        store.savePermit(id, DrugResponseDTO.PermitInfoDTO.builder().itemSeq(id).mainIngredient("Retained").build());
        store.saveProduct(DrugCategory.WOUND_CARE, item(id).toBuilder().name("Updated").build(), 0);
        em.flush();
        em.clear();

        assertThat(products.count()).isOne();
        assertThat(query.getDrugList("athletes-foot", null, 0, 20).getItems()).singleElement()
                .satisfies(drug -> {
                    assertThat(drug.getName()).isEqualTo("Updated");
                    assertThat(drug.getCategory()).isEqualTo("athletes-foot");
                    assertThat(drug.getPermit().getMainIngredient()).isEqualTo("Retained");
                });
        assertThat(query.getDrugList("wound-care", null, 0, 20).getTotalElements()).isOne();
    }

    @Test
    void databaseSearchIsCaseInsensitiveAndTreatsWildcardsLiterally() {
        store.saveProduct(DrugCategory.ATHLETES_FOOT, item("199400001").toBuilder().name("Cream 10%").build(), 0);
        store.saveProduct(DrugCategory.ATHLETES_FOOT, item("199400002").toBuilder().name("Cream A_B").build(), 1);

        assertThat(query.getDrugList("athletes-foot", " CREAM ", 0, 20).getTotalElements()).isEqualTo(2);
        assertThat(query.getDrugList("athletes-foot", "%", 0, 20).getItems()).singleElement()
                .satisfies(drug -> assertThat(drug.getId()).isEqualTo("199400001"));
        assertThat(query.getDrugList("athletes-foot", "_", 0, 20).getItems()).singleElement()
                .satisfies(drug -> assertThat(drug.getId()).isEqualTo("199400002"));
        assertThat(query.getDrugList("athletes-foot", "not-found", 0, 20).getItems()).isEmpty();
    }

    @Test
    void emptyCatalogReportsNotReadyAndMissingDetailsNeverCallExternalApi() {
        assertError(() -> query.getDrugList("athletes-foot", null, 0, 20), ErrorStatus.DRUG_DATA_SERVICE_UNAVAILABLE);
        assertError(() -> query.getPermitInfo("199400709"), ErrorStatus.DRUG_PERMIT_NOT_FOUND);
        assertError(() -> query.getPermitInfo("online-product"), ErrorStatus._BAD_REQUEST);
        store.saveProduct(DrugCategory.ATHLETES_FOOT, item("199400709"), 0);
        assertError(() -> query.getPermitInfo("199400709"), ErrorStatus.DRUG_PERMIT_NOT_FOUND);
    }

    @Test
    void absentRecallIsReportedAsUnavailableRatherThanClear() {
        store.saveProduct(DrugCategory.ATHLETES_FOOT, item("199400709"), 0);
        store.savePermit("199400709", DrugResponseDTO.PermitInfoDTO.builder().itemSeq("199400709").build());
        var recall = query.getPermitInfo("199400709").getRecall();
        assertThat(recall.getStatus()).isEqualTo("unavailable");
        assertThat(recall.getCheckedAt()).isNull();
        assertThat(recall.getRecords()).isEmpty();
    }

    @Test
    void batchedRefreshAndFailedDetailFetchKeepPreviouslyStoredPermit() {
        String id = "199400709";
        store.saveProducts(DrugCategory.ATHLETES_FOOT, List.of(item(id), item("199400710")), 0);
        store.saveDetails(id, true, DrugResponseDTO.PermitInfoDTO.builder().itemSeq(id)
                .mainIngredient("Retained").build(), DrugResponseDTO.RecallInfoDTO.builder()
                .status("alert").checkedAt("2026-10-17T00:00:00Z")
                .records(List.of(DrugResponseDTO.RecallRecordDTO.builder().reason("Retained recall").build()))
                .build());
        store.saveProducts(DrugCategory.ATHLETES_FOOT, List.of(item(id).toBuilder().name("Updated").build()), 0);
        store.saveDetails(id, false, null,
                DrugResponseDTO.RecallInfoDTO.builder().status("unavailable").records(List.of()).build());
        em.flush();
        em.clear();

        assertThat(products.count()).isEqualTo(2);
        var detail = query.getPermitInfo(id);
        assertThat(detail.getDrug().getName()).isEqualTo("Updated");
        assertThat(detail.getPermit().getMainIngredient()).isEqualTo("Retained");
        assertThat(detail.getRecall().getStatus()).isEqualTo("unavailable");
        assertThat(detail.getRecall().getCheckedAt()).isEqualTo("2026-10-17T00:00:00Z");
        assertThat(detail.getRecall().getRecords()).singleElement()
                .satisfies(record -> assertThat(record.getReason()).isEqualTo("Retained recall"));
    }

    private DrugResponseDTO.DrugItemDTO item(String id) {
        return DrugResponseDTO.DrugItemDTO.builder().id(id).name("Stored product " + id).company("Maker")
                .image("https://bucket.s3.amazonaws.com/drug-images/" + id + ".jpg")
                .efficacy("Stored efficacy").usage("Stored usage").warning("Stored warning")
                .interaction("Stored interaction").sideEffect("Stored side effects").storage("Stored storage")
                .category("athletes-foot").online(false).build();
    }

    private void assertError(Runnable action, ErrorStatus status) {
        assertThatThrownBy(action::run).isInstanceOf(GeneralException.class)
                .satisfies(error -> assertThat(((GeneralException) error).getCode()).isEqualTo(status));
    }
}
