package com.feetfit.server.web.controller;

import com.feetfit.server.config.InternalApiKeyInterceptor;
import com.feetfit.server.domain.Shoe;
import com.feetfit.server.domain.enums.ShoeLabCharacteristic;
import com.feetfit.server.repository.ShoeLabMeasurementRepository;
import com.feetfit.server.repository.ShoeLabMetricRepository;
import com.feetfit.server.repository.ShoeImportAuditRepository;
import com.feetfit.server.repository.ShoeRepository;
import com.feetfit.server.repository.ShoeReviewRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:shoe-http-ingestion;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.hikari.connection-init-sql=",
        "INTERNAL_API_KEY=test-service-key",
        "jwt.secret=dGhpcy1pcy1hLXZlcnktbG9uZy10ZXN0LXNlY3JldC0zMi1ieXRlcw=="
})
class ShoeIngestionHttpJpaIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ShoeRepository shoeRepository;
    @Autowired ShoeReviewRepository shoeReviewRepository;
    @Autowired ShoeLabMeasurementRepository labMeasurementRepository;
    @Autowired ShoeLabMetricRepository labMetricRepository;
    @Autowired ShoeImportAuditRepository auditRepository;

    @Test
    void crawlerContractsTravelThroughHttpAndPersistMusinsaThenRunRepeat() throws Exception {
        mockMvc.perform(post("/internal/shoes/imports/musinsa")
                        .header(InternalApiKeyInterceptor.INTERNAL_API_KEY_HEADER, "test-service-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source":"MUSINSA",
                                  "collectedAt":"2026-08-23T17:00:00",
                                  "shoes":[{
                                    "goodsNo":"6371095",
                                    "brandName":"아디다스",
                                    "shoeName":"도쿄 메리제인 - 화이트:블랙 / KI3032",
                                    "modelCode":"KI3032",
                                    "musinsaUrl":"https://www.musinsa.com/products/6371095",
                                    "sourceBrandKey":"adidas",
                                    "price":108990,
                                    "imageUrl":null,
                                    "overallRating":4.9,
                                    "reviewCount":48,
                                    "reviews":[{
                                      "sourceReviewId":"86933971",
                                      "rating":5.0,
                                      "reviewText":"발볼이 편하고\\n오래 신어도 괜찮았어요.",
                                      "collectedAt":"2026-08-23T17:00:00"
                                    }]
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.requestedCount").value(1))
                .andExpect(jsonPath("$.result.processedCount").value(1))
                .andExpect(jsonPath("$.result.items[0].matchStatus").value("MATCHED"))
                .andExpect(jsonPath("$.result.items[0].operation").value("CREATED"));

        Shoe shoe = shoeRepository.findByMusinsaGoodsNo("6371095").orElseThrow();
        assertThat(shoe.getModelCode()).isEqualTo("KI3032");
        assertThat(shoeReviewRepository.findByShoeIdOrderByIdAsc(shoe.getId()))
                .singleElement()
                .satisfies(review -> {
                    assertThat(review.getSourceReviewId()).isEqualTo("86933971");
                    assertThat(review.getReviewText())
                            .isEqualTo("발볼이 편하고\n오래 신어도 괜찮았어요.");
                });

        mockMvc.perform(post("/internal/shoes/imports/runrepeat/targeted")
                        .header(InternalApiKeyInterceptor.INTERNAL_API_KEY_HEADER, "test-service-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source":"RUNREPEAT",
                                  "items":[{
                                    "externalKey":"6371095",
                                    "targetGoodsNo":"6371095",
                                    "brandName":"RunRepeat Brand",
                                    "shoeName":"RunRepeat Shoe",
                                    "modelCode":"RR-KI3032-SOURCE",
                                    "sourceUrl":"https://runrepeat.com/shoe-http",
                                    "capturedAt":"2026-08-23T17:05:00",
                                    "parserVersion":"runrepeat-html-v1",
                                    "testedSize":"US 9",
                                    "rawMetrics":[{
                                      "canonicalCharacteristic":"WIDTH_SPACE",
                                      "sourceMetricName":"Width / Fit",
                                      "value":93.400000,
                                      "averageValue":95.200000,
                                      "sourceMinValue":88.100000,
                                      "sourceMaxValue":102.600000,
                                      "unit":"mm",
                                      "testedSize":"US 9",
                                      "methodName":"caliper",
                                      "methodVersion":"2026",
                                      "location":"widest part",
                                      "comparisonSampleCount":242,
                                      "comparisonCohort":"running shoes",
                                      "rawValueText":"93.4 mm"
                                    }]
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.processedCount").value(1))
                .andExpect(jsonPath("$.result.items[0].matchStatus").value("MATCHED"))
                .andExpect(jsonPath("$.result.items[0].operation").value("CREATED"));

        var snapshots = labMeasurementRepository.findByShoeIdAndSourceOrderByCapturedAtDescIdDesc(
                shoe.getId(), "RUNREPEAT");
        assertThat(snapshots).singleElement()
                .satisfies(snapshot -> {
                    assertThat(snapshot.getTestedSize()).isEqualTo("US 9");
                    assertThat(snapshot.getSourceModelCode()).isEqualTo("RR-KI3032-SOURCE");
                });
        assertThat(labMetricRepository.findByLabMeasurementIdInOrderByIdAsc(
                snapshots.stream().map(snapshot -> snapshot.getId()).toList()))
                .singleElement()
                .satisfies(metric -> {
                    assertThat(metric.getCanonicalCharacteristic())
                            .isEqualTo(ShoeLabCharacteristic.WIDTH_SPACE);
                    assertThat(metric.getValue()).isEqualByComparingTo("93.400000");
                    assertThat(metric.getAverageValue()).isEqualByComparingTo("95.200000");
                });
    }

    @Test
    void runRepeatEndpointsRejectWrongTargetShapeBeforeAuditingOrPersisting() throws Exception {
        long auditsBefore = auditRepository.count();
        long snapshotsBefore = labMeasurementRepository.count();

        mockMvc.perform(post("/internal/shoes/imports/runrepeat")
                        .header(InternalApiKeyInterceptor.INTERNAL_API_KEY_HEADER, "test-service-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source":"RUNREPEAT",
                                  "items":[{
                                    "externalKey":"100",
                                    "targetGoodsNo":"100",
                                    "brandName":"Brand",
                                    "shoeName":"Shoe",
                                    "sourceUrl":"https://runrepeat.com/wrong-legacy-shape",
                                    "capturedAt":"2026-08-23T18:00:00",
                                    "parserVersion":"runrepeat-html-v1",
                                    "rawMetrics":[]
                                  }]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        mockMvc.perform(post("/internal/shoes/imports/runrepeat/targeted")
                        .header(InternalApiKeyInterceptor.INTERNAL_API_KEY_HEADER, "test-service-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source":"RUNREPEAT",
                                  "items":[{
                                    "externalKey":"100",
                                    "brandName":"Brand",
                                    "shoeName":"Shoe",
                                    "sourceUrl":"https://runrepeat.com/missing-target",
                                    "capturedAt":"2026-08-23T18:00:00",
                                    "parserVersion":"runrepeat-html-v1",
                                    "rawMetrics":[]
                                  }]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        assertThat(auditRepository.count()).isEqualTo(auditsBefore);
        assertThat(labMeasurementRepository.count()).isEqualTo(snapshotsBefore);
    }
}
