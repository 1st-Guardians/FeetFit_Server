package com.feetfit.server.service.ShoeService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feetfit.server.domain.Shoe;
import com.feetfit.server.domain.enums.ShoeImportMatchStatus;
import com.feetfit.server.domain.enums.ShoeImportOperation;
import com.feetfit.server.domain.enums.ShoeImportSource;
import com.feetfit.server.domain.enums.ShoeLabCharacteristic;
import com.feetfit.server.repository.ShoeImportAuditRepository;
import com.feetfit.server.repository.ShoeLabMeasurementRepository;
import com.feetfit.server.repository.ShoeRepository;
import com.feetfit.server.repository.ShoeReviewRepository;
import com.feetfit.server.web.dto.shoe.ShoeIngestionRequestDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:shoe-ingestion;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE"
})
@Import({ShoeIngestionServiceImpl.class, ShoeIngestionJpaIntegrationTest.Config.class})
class ShoeIngestionJpaIntegrationTest {

    @Autowired ShoeIngestionServiceImpl service;
    @Autowired ShoeRepository shoeRepository;
    @Autowired ShoeReviewRepository shoeReviewRepository;
    @Autowired ShoeLabMeasurementRepository labRepository;
    @Autowired ShoeImportAuditRepository auditRepository;

    @Test
    void storesMusinsaThenUpsertsNullableEnrichmentAndStableReviewIdentity() {
        service.importMusinsa(musinsa(159000, "https://image/first", 4.7f, 1,
                review("review-1", 4.5f, "original")));
        service.importMusinsa(musinsa(null, null, null, 2,
                review("review-1", 4.0f, "updated")));

        Shoe stored = shoeRepository.findByMusinsaGoodsNo("goods-100").orElseThrow();
        assertThat(stored.getPrice()).isEqualTo(159000);
        assertThat(stored.getImageUrl()).isEqualTo("https://image/first");
        assertThat(stored.getOverallRating()).isEqualTo(4.7f);
        assertThat(stored.getReviewCount()).isEqualTo(2);
        assertThat(shoeReviewRepository.findByShoeIdOrderByIdAsc(stored.getId()))
                .singleElement()
                .satisfies(review -> {
                    assertThat(review.getSourceReviewId()).isEqualTo("review-1");
                    assertThat(review.getReviewText()).isEqualTo("updated");
                    assertThat(review.getRating()).isEqualTo(4.0f);
                });
    }

    @Test
    void storesRawSnapshotIdempotentlyAndStagesUnmatchedAndAmbiguousItems() {
        service.importMusinsa(musinsa(159000, null, 4.7f, 0));
        LocalDateTime firstCapture = LocalDateTime.of(2026, 8, 23, 12, 0);

        var created = service.importRunRepeat(runRepeat(
                "rr-matched", "MODEL-1", firstCapture, new BigDecimal("98.000000")));
        var updated = service.importRunRepeat(runRepeat(
                "rr-matched", "MODEL-1", firstCapture, new BigDecimal("98.100000")));
        service.importRunRepeat(runRepeat(
                "rr-next", "MODEL-1", firstCapture.plusDays(1), new BigDecimal("98.200000")));

        Shoe matchedShoe = shoeRepository.findByMusinsaGoodsNo("goods-100").orElseThrow();
        assertThat(created.getItems().get(0).getOperation()).isEqualTo(ShoeImportOperation.CREATED);
        assertThat(updated.getItems().get(0).getOperation()).isEqualTo(ShoeImportOperation.UPDATED);
        assertThat(labRepository.findByShoeIdAndSourceOrderByCapturedAtDescIdDesc(
                matchedShoe.getId(), "RUNREPEAT")).hasSize(2);
        assertThat(labRepository.findBySnapshotKey(
                labRepository.findByShoeIdAndSourceOrderByCapturedAtDescIdDesc(
                        matchedShoe.getId(), "RUNREPEAT").get(1).getSnapshotKey()))
                .get().satisfies(snapshot -> assertThat(snapshot.getRawMetrics())
                        .singleElement()
                        .satisfies(metric -> assertThat(metric.getValue())
                                .isEqualByComparingTo("98.100000")));

        var unmatched = service.importRunRepeat(runRepeat(
                "rr-unmatched", "NO-MATCH", firstCapture, BigDecimal.ONE));
        shoeRepository.save(shoe("duplicate-1", "DUPLICATE"));
        shoeRepository.save(shoe("duplicate-2", "DUPLICATE"));
        var ambiguous = service.importRunRepeat(runRepeat(
                "rr-ambiguous", "DUPLICATE", firstCapture, BigDecimal.ONE));

        assertThat(unmatched.getItems().get(0).getMatchStatus())
                .isEqualTo(ShoeImportMatchStatus.UNMATCHED);
        assertThat(ambiguous.getItems().get(0).getMatchStatus())
                .isEqualTo(ShoeImportMatchStatus.AMBIGUOUS);
        assertThat(auditRepository.count()).isEqualTo(6);
        assertThat(auditRepository.findAll())
                .filteredOn(audit -> audit.getMatchStatus() != ShoeImportMatchStatus.MATCHED)
                .allSatisfy(audit -> assertThat(audit.getRawPayload()).isNotBlank());
    }

    @Test
    void matchesBrandAndNormalizedProductNameOnlyWhenModelCodeIsAbsent() {
        service.importMusinsa(musinsa(159000, null, 4.7f, 0));

        var result = service.importRunRepeat(runRepeatWithIdentity(
                "rr-normalized-name",
                null,
                " brand ",
                "Ｓｈｏｅ",
                LocalDateTime.of(2026, 8, 23, 12, 0),
                new BigDecimal("98.000000")));

        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getMatchStatus()).isEqualTo(ShoeImportMatchStatus.MATCHED);
            assertThat(item.getOperation()).isEqualTo(ShoeImportOperation.CREATED);
            assertThat(item.getShoeId()).isEqualTo(
                    shoeRepository.findByMusinsaGoodsNo("goods-100").orElseThrow().getId());
        });
    }

    @Test
    void musinsaTreatsSharedModelCodeAsDistinctColourSkusAndRemainsIdempotent() {
        service.importMusinsa(musinsaProduct(
                "goods-colour-a", "MODEL-SHARED", "https://musinsa/goods-colour-a"));
        var created = service.importMusinsa(musinsaProduct(
                "goods-colour-b", "MODEL-SHARED", "https://musinsa/goods-colour-b"));
        var updated = service.importMusinsa(musinsaProduct(
                "goods-colour-b", "MODEL-SHARED", "https://musinsa/goods-colour-b"));

        assertThat(shoeRepository.findAll()).hasSize(2);
        assertThat(shoeRepository.findByMusinsaGoodsNo("goods-colour-a")).isPresent();
        assertThat(shoeRepository.findByMusinsaGoodsNo("goods-colour-b")).isPresent();
        assertThat(created.getItems()).singleElement()
                .extracting("operation")
                .isEqualTo(ShoeImportOperation.CREATED);
        assertThat(updated.getItems()).singleElement()
                .extracting("operation")
                .isEqualTo(ShoeImportOperation.UPDATED);
    }

    @Test
    void musinsaStagesUrlOwnedByAnotherGoodsNoInsteadOfReassigningIdentity() {
        service.importMusinsa(musinsaProduct(
                "goods-owner", "MODEL-OWNER", "https://musinsa/shared-url"));

        var result = service.importMusinsa(musinsaProduct(
                "goods-intruder", "MODEL-INTRUDER", "https://musinsa/shared-url"));

        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getMatchStatus()).isEqualTo(ShoeImportMatchStatus.AMBIGUOUS);
            assertThat(item.getOperation()).isEqualTo(ShoeImportOperation.STAGED);
        });
        assertThat(shoeRepository.findAll()).singleElement()
                .satisfies(shoe -> assertThat(shoe.getMusinsaGoodsNo()).isEqualTo("goods-owner"));
        assertThat(shoeRepository.findByMusinsaGoodsNo("goods-intruder")).isEmpty();
    }

    @Test
    void targetedRunRepeatUsesExactGoodsNoAndIdempotentlyReplacesRawMetrics() {
        service.importMusinsa(musinsa(159000, null, 4.7f, 0));
        LocalDateTime capturedAt = LocalDateTime.of(2026, 8, 23, 13, 0);

        var created = service.importRunRepeatTargeted(targetedRunRepeat(
                "goods-100", " goods-100 ", "RR-SOURCE-SKU",
                capturedAt, new BigDecimal("98.000000")));
        var updated = service.importRunRepeatTargeted(targetedRunRepeat(
                "goods-100", " goods-100 ", "RR-SOURCE-SKU",
                capturedAt, new BigDecimal("99.100000")));

        Shoe matchedShoe = shoeRepository.findByMusinsaGoodsNo("goods-100").orElseThrow();
        assertThat(created.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getShoeId()).isEqualTo(matchedShoe.getId());
            assertThat(item.getMatchStatus()).isEqualTo(ShoeImportMatchStatus.MATCHED);
            assertThat(item.getOperation()).isEqualTo(ShoeImportOperation.CREATED);
        });
        assertThat(updated.getItems()).singleElement()
                .extracting("operation")
                .isEqualTo(ShoeImportOperation.UPDATED);

        assertThat(labRepository.findByShoeIdAndSourceOrderByCapturedAtDescIdDesc(
                matchedShoe.getId(), "RUNREPEAT"))
                .singleElement()
                .satisfies(snapshot -> {
                    assertThat(snapshot.getSourceModelCode()).isEqualTo("RR-SOURCE-SKU");
                    assertThat(snapshot.getRawMetrics()).singleElement()
                            .satisfies(metric -> assertThat(metric.getValue())
                                    .isEqualByComparingTo("99.100000"));
                });
        assertThat(auditRepository.findAll())
                .filteredOn(audit -> audit.getSource() == ShoeImportSource.RUNREPEAT)
                .allSatisfy(audit -> assertThat(audit.getRawPayload())
                        .contains("\"targetGoodsNo\":\" goods-100 \"")
                        .contains("\"modelCode\":\"RR-SOURCE-SKU\""));
    }

    @Test
    void targetedRunRepeatDoesNotFallbackAndRejectsCaseMismatchedGoodsNo() {
        service.importMusinsa(musinsa(159000, null, 4.7f, 0));
        LocalDateTime capturedAt = LocalDateTime.of(2026, 8, 23, 14, 0);

        var missing = service.importRunRepeatTargeted(targetedRunRepeat(
                "missing-goods", "missing-goods", "MODEL-1",
                capturedAt, BigDecimal.ONE));
        var caseMismatch = service.importRunRepeatTargeted(targetedRunRepeat(
                "GOODS-100", "GOODS-100", "MODEL-1",
                capturedAt, BigDecimal.ONE));

        assertThat(missing.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getMatchStatus()).isEqualTo(ShoeImportMatchStatus.UNMATCHED);
            assertThat(item.getOperation()).isEqualTo(ShoeImportOperation.STAGED);
        });
        assertThat(caseMismatch.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getMatchStatus()).isEqualTo(ShoeImportMatchStatus.UNMATCHED);
            assertThat(item.getOperation()).isEqualTo(ShoeImportOperation.STAGED);
        });
        Shoe shoe = shoeRepository.findByMusinsaGoodsNo("goods-100").orElseThrow();
        assertThat(labRepository.findByShoeIdAndSourceOrderByCapturedAtDescIdDesc(
                shoe.getId(), "RUNREPEAT")).isEmpty();
    }

    private ShoeIngestionRequestDTO.MusinsaImportRequest musinsa(
            Integer price, String imageUrl, Float rating, int reviewCount,
            ShoeIngestionRequestDTO.MusinsaReviewItem... reviews) {
        return ShoeIngestionRequestDTO.MusinsaImportRequest.builder()
                .source(ShoeImportSource.MUSINSA)
                .collectedAt(LocalDateTime.of(2026, 8, 23, 11, 0))
                .shoes(List.of(ShoeIngestionRequestDTO.MusinsaShoeItem.builder()
                        .goodsNo("goods-100")
                        .brandName("Brand")
                        .shoeName("Shoe")
                        .modelCode("MODEL-1")
                        .musinsaUrl("https://musinsa/goods-100")
                        .price(price)
                        .imageUrl(imageUrl)
                        .overallRating(rating)
                        .reviewCount(reviewCount)
                        .reviews(List.of(reviews))
                        .build()))
                .build();
    }

    private ShoeIngestionRequestDTO.MusinsaImportRequest musinsaProduct(
            String goodsNo,
            String modelCode,
            String musinsaUrl) {
        return ShoeIngestionRequestDTO.MusinsaImportRequest.builder()
                .source(ShoeImportSource.MUSINSA)
                .collectedAt(LocalDateTime.of(2026, 8, 23, 11, 0))
                .shoes(List.of(ShoeIngestionRequestDTO.MusinsaShoeItem.builder()
                        .goodsNo(goodsNo)
                        .brandName("Brand")
                        .shoeName("Shoe")
                        .modelCode(modelCode)
                        .musinsaUrl(musinsaUrl)
                        .price(159000)
                        .imageUrl("https://image/" + goodsNo)
                        .overallRating(4.7f)
                        .reviewCount(0)
                        .reviews(List.of())
                        .build()))
                .build();
    }

    private ShoeIngestionRequestDTO.MusinsaReviewItem review(
            String sourceReviewId, Float rating, String text) {
        return ShoeIngestionRequestDTO.MusinsaReviewItem.builder()
                .sourceReviewId(sourceReviewId)
                .rating(rating)
                .reviewText(text)
                .collectedAt(LocalDateTime.of(2026, 8, 23, 11, 0))
                .build();
    }

    private ShoeIngestionRequestDTO.RunRepeatImportRequest runRepeat(
            String externalKey, String modelCode, LocalDateTime capturedAt, BigDecimal width) {
        return runRepeatWithIdentity(
                externalKey, modelCode, "Brand", "Shoe", capturedAt, width);
    }

    private ShoeIngestionRequestDTO.RunRepeatImportRequest runRepeatWithIdentity(
            String externalKey, String modelCode, String brandName, String shoeName,
            LocalDateTime capturedAt, BigDecimal width) {
        return runRepeatPayload(
                externalKey, null, modelCode, brandName, shoeName, capturedAt, width);
    }

    private ShoeIngestionRequestDTO.RunRepeatImportRequest targetedRunRepeat(
            String externalKey,
            String targetGoodsNo,
            String modelCode,
            LocalDateTime capturedAt,
            BigDecimal width) {
        return runRepeatPayload(
                externalKey,
                targetGoodsNo,
                modelCode,
                "RunRepeat Brand",
                "RunRepeat Shoe",
                capturedAt,
                width);
    }

    private ShoeIngestionRequestDTO.RunRepeatImportRequest runRepeatPayload(
            String externalKey,
            String targetGoodsNo,
            String modelCode,
            String brandName,
            String shoeName,
            LocalDateTime capturedAt,
            BigDecimal width) {
        var metric = ShoeIngestionRequestDTO.RawMetricItem.builder()
                .canonicalCharacteristic(ShoeLabCharacteristic.WIDTH_SPACE)
                .sourceMetricName("Toebox width at the widest part")
                .value(width)
                .averageValue(new BigDecimal("95.200000"))
                .unit("mm")
                .testedSize("US 9")
                .methodName("caliper")
                .methodVersion("2026")
                .location("widest-part")
                .comparisonSampleCount(100)
                .comparisonCohort("road-running")
                .rawValueText(width + " mm")
                .build();
        return ShoeIngestionRequestDTO.RunRepeatImportRequest.builder()
                .source(ShoeImportSource.RUNREPEAT)
                .items(List.of(ShoeIngestionRequestDTO.RunRepeatSnapshotItem.builder()
                        .externalKey(externalKey)
                        .targetGoodsNo(targetGoodsNo)
                        .brandName(brandName)
                        .shoeName(shoeName)
                        .modelCode(modelCode)
                        .sourceUrl("https://runrepeat/" + externalKey)
                        .testedSize("US 9")
                        .widthMm(width.floatValue())
                        .capturedAt(capturedAt)
                        .parserVersion("rr-parser-1")
                        .rawMetrics(List.of(metric))
                        .build()))
                .build();
    }

    private Shoe shoe(String goodsNo, String modelCode) {
        return Shoe.builder()
                .musinsaGoodsNo(goodsNo)
                .brandName("Brand")
                .shoeName(goodsNo)
                .modelCode(modelCode)
                .musinsaUrl("https://musinsa/" + goodsNo)
                .clickCount(0)
                .reviewCount(0)
                .build();
    }

    @TestConfiguration
    static class Config {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
