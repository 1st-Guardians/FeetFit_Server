package com.feetfit.server.service.ShoeService;

import com.feetfit.server.domain.Shoe;
import com.feetfit.server.domain.ShoeLabMeasurement;
import com.feetfit.server.domain.ShoeLabMetric;
import com.feetfit.server.domain.enums.ShoeLabCharacteristic;
import com.feetfit.server.repository.ShoeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:recommendation-eligibility;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE"
})
@Import(ShoeRecommendationEligibilityService.class)
class ShoeRecommendationEligibilityJpaIntegrationTest {

    @Autowired TestEntityManager entityManager;
    @Autowired ShoeRepository shoeRepository;
    @Autowired ShoeRecommendationEligibilityService eligibilityService;

    @Test
    void newerIncompleteSnapshotExcludesOnlyRecommendationWhileKeepingCatalogShoe() {
        Shoe incompleteLatest = persistShoe("incomplete");
        Shoe eligible = persistShoe("eligible");

        ShoeLabMeasurement oldComplete = persistMeasurement(
                incompleteLatest, "old-complete", LocalDateTime.of(2026, 1, 1, 0, 0));
        persistCompleteMetrics(oldComplete);
        ShoeLabMeasurement newMissingBreathability = persistMeasurement(
                incompleteLatest, "new-incomplete", LocalDateTime.of(2026, 2, 1, 0, 0));
        persistMetric(newMissingBreathability, ShoeLabCharacteristic.WIDTH_SPACE, "1");
        persistMetric(newMissingBreathability, ShoeLabCharacteristic.CUSHION, "2");

        ShoeLabMeasurement eligibleLatest = persistMeasurement(
                eligible, "eligible-latest", LocalDateTime.of(2026, 2, 1, 0, 0));
        persistCompleteMetrics(eligibleLatest);
        entityManager.flush();
        entityManager.clear();

        assertThat(shoeRepository.count()).isEqualTo(2);
        assertThat(eligibilityService.findEligibleShoeIds()).containsExactly(eligible.getId());
        assertThat(eligibilityService.countEligibleShoes()).isEqualTo(1);
        Page<Shoe> page = eligibilityService.findEligibleShoes(PageRequest.of(0, 100));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(Shoe::getId)
                .containsExactly(eligible.getId());
    }

    private Shoe persistShoe(String suffix) {
        return entityManager.persistAndFlush(Shoe.builder()
                .brandName("brand-" + suffix)
                .shoeName("shoe-" + suffix)
                .modelCode("model-" + suffix)
                .musinsaGoodsNo("goods-" + suffix)
                .musinsaUrl("https://example.com/" + suffix)
                .build());
    }

    private ShoeLabMeasurement persistMeasurement(
            Shoe shoe, String snapshotKey, LocalDateTime capturedAt) {
        return entityManager.persistAndFlush(ShoeLabMeasurement.builder()
                .shoe(shoe)
                .source("RUNREPEAT")
                .sourceUrl("https://runrepeat.com/" + snapshotKey)
                .snapshotKey(snapshotKey)
                .capturedAt(capturedAt)
                .build());
    }

    private void persistCompleteMetrics(ShoeLabMeasurement measurement) {
        persistMetric(measurement, ShoeLabCharacteristic.TOEBOX_SPACE, "1");
        persistMetric(measurement, ShoeLabCharacteristic.SHOCK_ABSORPTION, "2");
        persistMetric(measurement, ShoeLabCharacteristic.BREATHABILITY, "3");
    }

    private void persistMetric(
            ShoeLabMeasurement measurement,
            ShoeLabCharacteristic characteristic,
            String value) {
        entityManager.persistAndFlush(ShoeLabMetric.builder()
                .labMeasurement(measurement)
                .canonicalCharacteristic(characteristic)
                .sourceMetricName(characteristic.name())
                .value(new BigDecimal(value))
                .build());
    }
}
