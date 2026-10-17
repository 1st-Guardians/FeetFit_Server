package com.feetfit.drugimport;

import com.feetfit.server.config.WebClientConfig;
import com.feetfit.server.domain.drug.DrugProduct;
import com.feetfit.server.domain.enums.DrugCategory;
import com.feetfit.server.repository.drug.DrugProductRepository;
import com.feetfit.server.service.DrugService.DrugCatalogMapper;
import com.feetfit.server.service.DrugService.DrugCatalogStore;
import com.feetfit.server.service.DrugService.DrugCatalogSyncService;
import com.feetfit.server.service.DrugService.DrugDataClient;
import com.feetfit.server.service.DrugService.DrugQueryServiceImpl;
import com.feetfit.server.service.DrugService.DrugRecommendationSummaryClient;
import com.feetfit.server.service.DrugService.DrugRecommendationPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EnableJpaAuditing
@EntityScan(basePackageClasses = DrugProduct.class)
@EnableJpaRepositories(basePackageClasses = DrugProductRepository.class)
@Import({WebClientConfig.class, DrugDataClient.class, DrugCatalogMapper.class, DrugCatalogStore.class,
        DrugCatalogSyncService.class, DrugQueryServiceImpl.class,
        DrugRecommendationSummaryClient.class, DrugRecommendationPolicy.class})
public class DrugCatalogImportApplication {
    public static void main(String[] args) throws Exception {
        try (var context = new SpringApplicationBuilder(DrugCatalogImportApplication.class)
                .web(WebApplicationType.NONE).run(args)) {
            if (context.getEnvironment().getProperty("drug.catalog.refresh-eligibility-only", Boolean.class, false)) {
                context.getBean(DrugCatalogStore.class).refreshEligibility();
                log.info("Drug catalog recommendation eligibility refreshed from stored permit and recall information.");
                return;
            }
            if (context.getEnvironment().getProperty("drug.recommendation.check-model", Boolean.class, false)) {
                var client = context.getBean(DrugRecommendationSummaryClient.class);
                if (!client.isModelAvailable()) {
                    throw new IllegalStateException("The configured drug recommendation model is unavailable: " + client.model());
                }
                log.info("Drug recommendation model is available. model={}", client.model());
                if (context.getEnvironment().getProperty("drug.recommendation.smoke", Boolean.class, false)) {
                    var basis = context.getBean(DrugRecommendationPolicy.class).rank(80f, 35f, 18f, 12f);
                    log.info("Drug recommendation summary smoke test with synthetic data: {}", client.generate(basis));
                }
                return;
            }
            if (context.getEnvironment().getProperty("drug.catalog.verify-only", Boolean.class, false)) {
                verifyCatalog(context.getBean(DrugQueryServiceImpl.class), context.getBean(JdbcTemplate.class));
                return;
            }
            var result = context.getBean(DrugCatalogSyncService.class).synchronize();
            if (result.failedCategories() > 0 || result.failedDetails() > 0) {
                throw new IllegalStateException("Drug catalog import was incomplete: " + result);
            }
            verifyCatalog(context.getBean(DrugQueryServiceImpl.class), context.getBean(JdbcTemplate.class));
        }
    }

    private static void verifyCatalog(DrugQueryServiceImpl query, JdbcTemplate jdbc) {
        log.info("Drug catalog storage verified. totals={}", jdbc.queryForMap("""
                select count(*) as products,
                       sum(case when online = false and permit_payload is not null then 1 else 0 end) as permits,
                       sum(case when recall_payload is not null then 1 else 0 end) as recalls,
                       sum(octet_length(item_payload) + coalesce(octet_length(permit_payload), 0)
                           + coalesce(octet_length(recall_payload), 0)) as payload_bytes
                from drug_product
                """));
        for (DrugCategory category : DrugCategory.values()) {
            long started = System.nanoTime();
            var page = query.getDrugList(category.getId(), null, 0, 20);
            log.info("Drug catalog DB list verified. category={}, total={}, pageSize={}, elapsedMs={}",
                    category.getId(), page.getTotalElements(), page.getItems().size(),
                    (System.nanoTime() - started) / 1_000_000);
            page.getItems().stream().filter(item -> !Boolean.TRUE.equals(item.getOnline()) && item.getPermit() != null)
                    .findFirst().ifPresent(item -> {
                        long detailStarted = System.nanoTime();
                        var detail = query.getPermitInfo(item.getId());
                        log.info("Drug catalog DB detail verified. itemSeq={}, recallStatus={}, elapsedMs={}",
                                item.getId(), detail.getRecall().getStatus(),
                                (System.nanoTime() - detailStarted) / 1_000_000);
                    });
        }
    }
}
