package com.feetfit.drugimport;

import com.feetfit.server.config.WebClientConfig;
import com.feetfit.server.domain.Report;
import com.feetfit.server.repository.ReportRepository;
import com.feetfit.server.service.DrugService.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EnableJpaAuditing
@EntityScan(basePackageClasses = Report.class)
@EnableJpaRepositories(basePackageClasses = ReportRepository.class)
@Import({WebClientConfig.class, DrugCatalogMapper.class, DrugCatalogStore.class, DrugRecommendationPolicy.class,
        DrugRecommendationSummaryClient.class, DrugRecommendationStore.class,
        DrugRecommendationGenerationService.class, DrugRecommendationQueryService.class})
public class DrugRecommendationBackfillApplication {
    public static void main(String[] args) {
        try (var context = new SpringApplicationBuilder(DrugRecommendationBackfillApplication.class)
                .web(WebApplicationType.NONE).run(args)) {
            boolean verifyOnly = context.getEnvironment().getProperty("drug.recommendation.verify-only", Boolean.class, false);
            if (!verifyOnly) {
                // Manage only the new table; existing measurement and report schemas are untouched.
                new ResourceDatabasePopulator(new ClassPathResource("db/manual/drug-recommendation.sql"))
                        .execute(context.getBean(DataSource.class));
                context.getBean(DrugCatalogStore.class).refreshEligibility();
                var store = context.getBean(DrugRecommendationStore.class);
                var generation = context.getBean(DrugRecommendationGenerationService.class);
                var reportIds = store.latestCompletedReportIds();
                log.info("Preparing drug recommendations for latest completed reports. count={}", reportIds.size());
                for (Long reportId : reportIds) {
                    store.prepareReport(reportId);
                    generation.generateNow(reportId);
                }
            }
            var jdbc = context.getBean(JdbcTemplate.class);
            log.info("Stored drug recommendation counts: {}", jdbc.queryForList(
                    "select status, summary_source, count(*) as records from drug_recommendation group by status, summary_source"));
            var users = jdbc.queryForList("""
                    select distinct r.user_id from drug_recommendation d join report r on r.id = d.report_id
                    """, Long.class);
            var query = context.getBean(DrugRecommendationQueryService.class);
            for (Long userId : users) {
                var result = query.get(userId, null, null, 0, 20);
                log.info("Drug recommendation DB read verified. reportId={}, model={}, source={}, priorities={}, pageSize={}, total={}",
                        result.getReportId(), result.getModel(), result.getSummarySource(),
                        result.getPriorities().stream().map(item -> item.getCategory()).toList(),
                        result.getItems().size(), result.getTotalElements());
            }
        }
    }
}
