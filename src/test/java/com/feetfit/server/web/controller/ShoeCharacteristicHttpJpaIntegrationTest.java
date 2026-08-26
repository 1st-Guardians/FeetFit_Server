package com.feetfit.server.web.controller;

import com.feetfit.server.config.InternalApiKeyInterceptor;
import com.feetfit.server.domain.Shoe;
import com.feetfit.server.repository.ShoeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-to-JPA contract test for the public, objective RunRepeat characteristics API.
 * Test data enters through the same Phase A ingestion boundary used by shoe_crawler.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:shoe-characteristics-http;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.hikari.connection-init-sql=",
        "internal.api-key=test-service-key",
        "jwt.secret=dGhpcy1pcy1hLXZlcnktbG9uZy10ZXN0LXNlY3JldC0zMi1ieXRlcw=="
})
class ShoeCharacteristicHttpJpaIntegrationTest {

    private static final String INTERNAL_API_KEY = "test-service-key";

    @Autowired MockMvc mockMvc;
    @Autowired ShoeRepository shoeRepository;

    @Test
    void publicCharacteristicsUseCompatibleLatestSnapshotsAndNeverFallBackToOlderSnapshot()
            throws Exception {
        importMusinsaShoes();
        importCompatibleWidthSnapshots();

        Shoe target = shoeRepository.findByModelCodeIgnoreCase("WIDTH-TARGET")
                .stream()
                .findFirst()
                .orElseThrow();
        Shoe noLab = shoeRepository.findByModelCodeIgnoreCase("NO-LAB")
                .stream()
                .findFirst()
                .orElseThrow();

        // The existing detail endpoint is reused for public basic product data.
        mockMvc.perform(get("/api/shoes/{shoeId}", target.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value(target.getId()))
                .andExpect(jsonPath("$.result.brandName").value("Test Brand"))
                .andExpect(jsonPath("$.result.shoeName").value("Width Target"))
                .andExpect(jsonPath("$.result.imageUrl")
                        .value("https://image.musinsa.com/width-target.jpg"))
                .andExpect(jsonPath("$.result.overallRating").value(4.8))
                .andExpect(jsonPath("$.result.price").value(120000))
                .andExpect(jsonPath("$.result.fitScore").value(nullValue()))
                .andExpect(jsonPath("$.result.reasons").isEmpty());

        // No Authorization header: objective product characteristics are public.
        mockMvc.perform(get("/api/shoes/{shoeId}/characteristics", target.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.result.shoeId").value(target.getId()))
                .andExpect(jsonPath("$.result.characteristics.length()").value(1))
                .andExpect(jsonPath("$.result.characteristics[0].type").value("WIDTH_SPACE"))
                .andExpect(jsonPath("$.result.characteristics[0].level").value("MEDIUM"))
                .andExpect(jsonPath("$.result.characteristics[0].value").value(93.4))
                .andExpect(jsonPath("$.result.characteristics[0].averageValue").value(95.2))
                .andExpect(jsonPath("$.result.characteristics[0].minValue").value(88.1))
                .andExpect(jsonPath("$.result.characteristics[0].maxValue").value(102.6))
                .andExpect(jsonPath("$.result.characteristics[0].unit").value("mm"))
                .andExpect(jsonPath("$.result.characteristics[0].testedSize").value("US 9"))
                .andExpect(jsonPath("$.result.characteristics[0].description").doesNotExist())
                .andExpect(jsonPath("$.result.summary")
                        .value("발볼 여유는 보통 수준인 신발입니다."));

        mockMvc.perform(get("/api/shoes/{shoeId}/characteristics", noLab.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.shoeId").value(noLab.getId()))
                .andExpect(jsonPath("$.result.characteristics").isEmpty())
                .andExpect(jsonPath("$.result.summary").value(nullValue()));

        importNewerPartialSnapshot();

        // The latest target snapshot has only CUSHION. WIDTH from its older
        // snapshot must not be silently carried forward into this response.
        mockMvc.perform(get("/api/shoes/{shoeId}/characteristics", target.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.characteristics.length()").value(1))
                .andExpect(jsonPath("$.result.characteristics[0].type").value("CUSHION"))
                .andExpect(jsonPath("$.result.characteristics[0].value").value(30.0))
                .andExpect(jsonPath("$.result.characteristics[0].level").value(nullValue()))
                .andExpect(jsonPath("$.result.characteristics[0].description").doesNotExist())
                .andExpect(jsonPath("$.result.summary").value(nullValue()));
    }

    private void importMusinsaShoes() throws Exception {
        mockMvc.perform(post("/internal/shoes/imports/musinsa")
                        .header(InternalApiKeyInterceptor.INTERNAL_API_KEY_HEADER, INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source":"MUSINSA",
                                  "collectedAt":"2026-08-23T17:00:00",
                                  "shoes":[
                                    {
                                      "goodsNo":"width-target",
                                      "brandName":"Test Brand",
                                      "shoeName":"Width Target",
                                      "modelCode":"WIDTH-TARGET",
                                      "musinsaUrl":"https://www.musinsa.com/products/width-target",
                                      "price":120000,
                                      "imageUrl":"https://image.musinsa.com/width-target.jpg",
                                      "overallRating":4.8,
                                      "reviewCount":0,
                                      "reviews":[]
                                    },
                                    {
                                      "goodsNo":"width-low",
                                      "brandName":"Test Brand",
                                      "shoeName":"Width Low",
                                      "modelCode":"WIDTH-LOW",
                                      "musinsaUrl":"https://www.musinsa.com/products/width-low",
                                      "price":110000,
                                      "imageUrl":null,
                                      "overallRating":4.7,
                                      "reviewCount":0,
                                      "reviews":[]
                                    },
                                    {
                                      "goodsNo":"width-high",
                                      "brandName":"Test Brand",
                                      "shoeName":"Width High",
                                      "modelCode":"WIDTH-HIGH",
                                      "musinsaUrl":"https://www.musinsa.com/products/width-high",
                                      "price":130000,
                                      "imageUrl":null,
                                      "overallRating":4.9,
                                      "reviewCount":0,
                                      "reviews":[]
                                    },
                                    {
                                      "goodsNo":"no-lab",
                                      "brandName":"Test Brand",
                                      "shoeName":"No Lab Shoe",
                                      "modelCode":"NO-LAB",
                                      "musinsaUrl":"https://www.musinsa.com/products/no-lab",
                                      "price":90000,
                                      "imageUrl":null,
                                      "overallRating":4.5,
                                      "reviewCount":0,
                                      "reviews":[]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.requestedCount").value(4))
                .andExpect(jsonPath("$.result.processedCount").value(4));
    }

    private void importCompatibleWidthSnapshots() throws Exception {
        mockMvc.perform(post("/internal/shoes/imports/runrepeat")
                        .header(InternalApiKeyInterceptor.INTERNAL_API_KEY_HEADER, INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source":"RUNREPEAT",
                                  "items":[
                                    %s,
                                    %s,
                                    %s
                                  ]
                                }
                                """.formatted(
                                widthSnapshot("target-width", "WIDTH-TARGET", "93.4", true),
                                widthSnapshot("low-width", "WIDTH-LOW", "90.0", false),
                                widthSnapshot("high-width", "WIDTH-HIGH", "100.0", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.requestedCount").value(3))
                .andExpect(jsonPath("$.result.processedCount").value(3));
    }

    private String widthSnapshot(
            String externalKey,
            String modelCode,
            String value,
            boolean includeSourceBounds) {
        String sourceBounds = includeSourceBounds
                ? "\"sourceMinValue\":88.1,\"sourceMaxValue\":102.6,"
                : "";
        return """
                {
                  "externalKey":"%s",
                  "brandName":"Test Brand",
                  "shoeName":"%s",
                  "modelCode":"%s",
                  "sourceUrl":"https://runrepeat.com/%s",
                  "capturedAt":"2026-08-23T17:05:00",
                  "parserVersion":"runrepeat-html-v1",
                  "testedSize":"US 9",
                  "rawMetrics":[{
                    "canonicalCharacteristic":"WIDTH_SPACE",
                    "sourceMetricName":"Width / Fit",
                    "value":%s,
                    "averageValue":95.2,
                    %s
                    "unit":"mm",
                    "testedSize":"US 9",
                    "methodName":null,
                    "methodVersion":null,
                    "location":"widest part",
                    "variant":null,
                    "comparisonSampleCount":242,
                    "comparisonCohort":"running shoes",
                    "rawValueText":"%s mm"
                  }]
                }
                """.formatted(
                externalKey,
                modelCode,
                modelCode,
                externalKey,
                value,
                sourceBounds,
                value);
    }

    private void importNewerPartialSnapshot() throws Exception {
        mockMvc.perform(post("/internal/shoes/imports/runrepeat")
                        .header(InternalApiKeyInterceptor.INTERNAL_API_KEY_HEADER, INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source":"RUNREPEAT",
                                  "items":[{
                                    "externalKey":"target-newer-partial",
                                    "brandName":"Test Brand",
                                    "shoeName":"Width Target",
                                    "modelCode":"WIDTH-TARGET",
                                    "sourceUrl":"https://runrepeat.com/target-newer-partial",
                                    "capturedAt":"2026-08-23T18:05:00",
                                    "parserVersion":"runrepeat-html-v1",
                                    "testedSize":"US 9",
                                    "rawMetrics":[{
                                      "canonicalCharacteristic":"CUSHION",
                                      "sourceMetricName":"Midsole softness",
                                      "value":30.0,
                                      "averageValue":35.0,
                                      "unit":"AC",
                                      "testedSize":"US 9",
                                      "methodName":"durometer",
                                      "methodVersion":"AC-2026",
                                      "location":null,
                                      "variant":"primary",
                                      "comparisonSampleCount":100,
                                      "comparisonCohort":"road running shoes",
                                      "rawValueText":"30.0 AC"
                                    }]
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.processedCount").value(1));
    }
}
