package com.feetfit.server.service.DrugService;

import com.feetfit.server.apiPayload.exception.GeneralException;
import com.feetfit.server.domain.*;
import com.feetfit.server.domain.enums.*;
import com.feetfit.server.repository.DrugRecommendationRepository;
import com.feetfit.server.repository.MetricAnalysisResultRepository;
import com.feetfit.server.repository.ReportRepository;
import com.feetfit.server.web.dto.drug.DrugResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Import({JacksonAutoConfiguration.class, DrugCatalogMapper.class, DrugCatalogStore.class,
        DrugRecommendationPolicy.class, DrugRecommendationStore.class, DrugRecommendationQueryService.class})
class DrugRecommendationJpaIntegrationTest {
    @Autowired TestEntityManager em;
    @Autowired DrugCatalogStore catalog;
    @Autowired DrugRecommendationStore store;
    @Autowired DrugCatalogMapper mapper;
    @Autowired DrugRecommendationQueryService query;
    @Autowired DrugRecommendationRepository recommendations;
    @Autowired MetricAnalysisResultRepository metrics;
    @Autowired ReportRepository reports;
    @MockBean DrugRecommendationSummaryClient client;
    @MockBean DrugRecommendationGenerationService generation;

    private User user;
    private Device device;
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 18, 10, 0);

    @BeforeEach
    void setup() {
        when(client.model()).thenReturn(DrugRecommendationSummaryClient.DEFAULT_MODEL);
        user = em.persist(User.builder().nickname("drug-test").socialId("drug-test")
                .socialType(SocialType.KAKAO).status(UserStatus.ACTIVE).build());
        device = em.persist(Device.builder().deviceName("drug-test").build());
    }

    @Test
    void savedPrioritiesDriveCrossCategoryPaginationWithoutCallingGptOnReads() {
        var report = report(user, now, MeasurementStatus.COMPLETED, 20f, 70f, 10f);
        for (int i = 0; i < 22; i++) product("wound-" + i, DrugCategory.WOUND_CARE, true, false, "clear");
        product("fungal", DrugCategory.ATHLETES_FOOT, true, false, "clear");
        product("bunion", DrugCategory.BUNION_BAND, true, false, "clear");
        var prepared = store.prepare(user.getId(), report.getMeasurementSession().getId());
        store.claim(prepared);
        store.complete(prepared, "Stored GPT summary explaining wound-care priority");
        em.flush();
        em.clear();
        clearInvocations(client, generation);

        var first = query.get(user.getId(), null, null, 0, 20);
        var second = query.get(user.getId(), null, null, 1, 20);

        assertThat(first.getPriorities()).extracting(DrugResponseDTO.DrugPriorityDTO::getCategory)
                .containsExactly("wound-care", "athletes-foot", "bunion-band");
        assertThat(first.getItems()).hasSize(20).allMatch(item -> item.getId().startsWith("wound-"));
        assertThat(second.getItems()).extracting(DrugResponseDTO.DrugItemDTO::getId)
                .containsExactly("wound-8", "wound-9", "fungal", "bunion");
        assertThat(first.getTotalElements()).isEqualTo(24);
        assertThat(first.getSummary()).isEqualTo("Stored GPT summary explaining wound-care priority");
        assertThat(first.getSummarySource()).isEqualTo("GPT");
        assertThat(first.getSummaryStatus()).isEqualTo("COMPLETED");
        assertThat(second.getSummary()).isEqualTo(first.getSummary());
        verifyNoInteractions(client, generation);
    }

    @Test
    void latestCompletedMeasurementWinsAndDataBelongsToAuthenticatedUser() {
        var old = report(user, now.minusDays(1), MeasurementStatus.COMPLETED, 95f, 95f, 45f);
        var latest = report(user, now, MeasurementStatus.COMPLETED, 90f, 10f, 10f);
        report(user, now.plusDays(1), MeasurementStatus.ANALYZING, 5f, 95f, 10f);
        var other = em.persist(User.builder().nickname("other").socialId("other")
                .socialType(SocialType.KAKAO).status(UserStatus.ACTIVE).build());
        var otherReport = report(other, now.plusDays(2), MeasurementStatus.COMPLETED, 0f, 90f, 10f);
        store.prepare(user.getId(), old.getMeasurementSession().getId());
        store.prepare(user.getId(), latest.getMeasurementSession().getId());
        store.prepare(other.getId(), otherReport.getMeasurementSession().getId());

        var result = query.get(user.getId(), null, null, 0, 20);
        assertThat(result.getReportId()).isEqualTo(latest.getId());
        assertThat(result.getPriorities().get(0).getCategory()).isEqualTo("athletes-foot");
        assertThat(reports.findLatestCompletedReportIds(MeasurementStatus.COMPLETED))
                .containsExactlyInAnyOrder(latest.getId(), otherReport.getId());
        assertThatThrownBy(() -> store.prepare(user.getId(), otherReport.getMeasurementSession().getId()))
                .isInstanceOf(GeneralException.class);
        assertThatThrownBy(() -> query.get(null, null, null, 0, 20)).isInstanceOf(GeneralException.class);
    }

    @Test
    void sameInputsAreIdempotentAndStaleGptResultCannotOverwriteUpdatedSnapshot() {
        var report = report(user, now, MeasurementStatus.COMPLETED, 90f, 10f, 10f);
        var first = store.prepare(user.getId(), report.getMeasurementSession().getId());
        assertThat(store.claim(first)).isTrue();
        assertThat(store.claim(first)).isFalse();
        assertThat(store.complete(first, "Stored GPT summary")).isTrue();
        var identical = store.prepare(user.getId(), report.getMeasurementSession().getId());
        assertThat(identical.inputHash()).isEqualTo(first.inputHash());
        assertThat(store.claim(identical)).isFalse();
        var fungal = metrics.findByReportIdAndMetricType(report.getId(), MetricType.ATHLETES_FOOT).orElseThrow();
        fungal.updateMetricResult(100f, GaugeStatus.VERY_GOOD, List.of("Updated"));
        em.flush();
        var updated = store.prepare(user.getId(), report.getMeasurementSession().getId());
        assertThat(updated.inputHash()).isNotEqualTo(first.inputHash());
        assertThat(store.complete(first, "Stale summary")).isFalse();
        assertThat(recommendations.findById(report.getId()).orElseThrow().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void cancelledRecalledAndUnverifiedDrugsAreExcludedFromPersonalRecommendations() {
        product("valid", DrugCategory.ATHLETES_FOOT, false, false, "clear");
        product("cancelled", DrugCategory.ATHLETES_FOOT, false, true, "clear");
        product("recalled", DrugCategory.ATHLETES_FOOT, false, false, "alert");
        product("unverified", DrugCategory.ATHLETES_FOOT, false, false, "unavailable");
        product("online", DrugCategory.BUNION_BAND, true, false, "unavailable");

        var result = query.get(user.getId(), null, null, 0, 20);
        assertThat(result.isHasMeasurement()).isFalse();
        assertThat(result.getItems()).extracting(DrugResponseDTO.DrugItemDTO::getId)
                .containsExactly("valid", "online");
        assertThat(result.getSummary()).isNull();
    }

    @Test
    void failedSummaryKeepsSavedPriorityAndAllowsExplicitRetry() {
        var report = report(user, now, MeasurementStatus.COMPLETED, 90f, 10f, 10f);
        var prepared = store.prepare(user.getId(), report.getMeasurementSession().getId());
        store.claim(prepared);
        store.fail(prepared);
        var result = query.get(user.getId(), null, null, 0, 20);
        assertThat(result.getSummaryStatus()).isEqualTo("FAILED");
        assertThat(result.getSummary()).isNotBlank();
        assertThat(result.getPriorities().get(0).getCategory()).isEqualTo("athletes-foot");
        assertThat(store.claim(prepared)).isTrue();
    }

    @Test
    void healthyMeasurementPersistsSkinFungalBandOrderAndStillReturnsProducts() {
        var report = report(user, now, MeasurementStatus.COMPLETED, 100f, 100f, 15f);
        product("fungal", DrugCategory.ATHLETES_FOOT, true, false, "clear");
        product("bunion", DrugCategory.BUNION_BAND, true, false, "clear");
        product("skin", DrugCategory.WOUND_CARE, true, false, "clear");
        var prepared = store.prepare(user.getId(), report.getMeasurementSession().getId());
        assertThat(prepared.basis().allHealthy()).isTrue();
        em.flush();
        em.clear();
        clearInvocations(client, generation);

        var result = query.get(user.getId(), null, null, 0, 20);

        assertThat(result.getPriorities()).extracting(DrugResponseDTO.DrugPriorityDTO::getCategory)
                .containsExactly("wound-care", "athletes-foot", "bunion-band");
        assertThat(result.getItems()).extracting(DrugResponseDTO.DrugItemDTO::getId)
                .containsExactly("skin", "fungal", "bunion");
        assertThat(result.getSummary()).isEqualTo(DrugRecommendationPolicy.HEALTHY_SUMMARY);
        assertThat(result.getRuleVersion()).isEqualTo(DrugRecommendationPolicy.RULE_VERSION);
        assertThat(result.getTotalElements()).isEqualTo(3);
        verifyNoInteractions(client, generation);
    }

    @Test
    void noCompletedMeasurementUsesSameDisplayOrderWithoutClaimingHealthy() {
        product("fungal", DrugCategory.ATHLETES_FOOT, true, false, "clear");
        product("bunion", DrugCategory.BUNION_BAND, true, false, "clear");
        product("skin", DrugCategory.WOUND_CARE, true, false, "clear");

        var result = query.get(user.getId(), null, null, 0, 20);

        assertThat(result.isHasMeasurement()).isFalse();
        assertThat(result.getPriorities()).isEmpty();
        assertThat(result.getSummary()).isNull();
        assertThat(result.getItems()).extracting(DrugResponseDTO.DrugItemDTO::getId)
                .containsExactly("skin", "fungal", "bunion");
    }

    @Test
    void approvedHealthyCopyInvalidatesPreviouslyGeneratedSummary() throws Exception {
        var report = report(user, now, MeasurementStatus.COMPLETED, 100f, 100f, 15f);
        var basis = new DrugRecommendationPolicy().rank(100f, 100f, 15f, 5f);
        String payload = mapper.serialize(basis);
        String previousHash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest((client.model() + "\n" + payload).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var previous = DrugRecommendation.builder().report(report).build();
        previous.prepare(payload, "Previous healthy wording", previousHash, client.model());
        em.persistAndFlush(previous);
        var oldPrepared = new DrugRecommendationStore.Prepared(report.getId(), previousHash, client.model(), basis);
        store.claim(oldPrepared);
        store.complete(oldPrepared, "Previously generated healthy wording");

        var updated = store.prepare(user.getId(), report.getMeasurementSession().getId());

        assertThat(updated.inputHash()).isNotEqualTo(previousHash);
        var result = query.get(user.getId(), null, null, 0, 20);
        assertThat(result.getSummary()).isEqualTo(DrugRecommendationPolicy.HEALTHY_SUMMARY);
        assertThat(result.getSummaryStatus()).isEqualTo("PENDING");
        assertThat(result.getPriorities()).extracting(DrugResponseDTO.DrugPriorityDTO::getCategory)
                .containsExactly("wound-care", "athletes-foot", "bunion-band");
    }

    private Report report(User owner, LocalDateTime measuredAt, MeasurementStatus status,
                          float skin, float fungal, float angle) {
        var session = em.persist(MeasurementSession.builder().user(owner).device(device)
                .measuredAt(measuredAt).status(status).build());
        var report = em.persist(Report.builder().user(owner).measurementSession(session)
                .reportDate(now).totalScore(80).build());
        em.persist(MetricAnalysisResult.builder().report(report).metricType(MetricType.SKIN_IRRITATION)
                .score(skin).status(GaugeStatus.ATTENTION_NEEDED).advice(List.of("Skin")).build());
        em.persist(MetricAnalysisResult.builder().report(report).metricType(MetricType.ATHLETES_FOOT)
                .score(fungal).status(GaugeStatus.ATTENTION_NEEDED).advice(List.of("Fungal")).build());
        em.persist(HalluxValgusAnalysis.builder().measurementSession(session)
                .leftToeAngleDegree(angle).rightToeAngleDegree(5f).riskScore(50f).build());
        em.flush();
        return report;
    }

    private void product(String id, DrugCategory category, boolean online, boolean cancelled, String recallStatus) {
        catalog.saveProduct(category, DrugResponseDTO.DrugItemDTO.builder().id(id).name(id)
                .category(category.getId()).online(online).build(), 0);
        catalog.saveDetails(id, true, DrugResponseDTO.PermitInfoDTO.builder().itemSeq(id).cancelled(cancelled).build(),
                DrugResponseDTO.RecallInfoDTO.builder().status(recallStatus).records(List.of()).build());
    }
}
