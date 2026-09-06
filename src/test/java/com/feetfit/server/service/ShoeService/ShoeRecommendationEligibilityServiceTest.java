package com.feetfit.server.service.ShoeService;

import com.feetfit.server.domain.Shoe;
import com.feetfit.server.domain.ShoeLabMeasurement;
import com.feetfit.server.domain.ShoeLabMetric;
import com.feetfit.server.domain.enums.ShoeLabCharacteristic;
import com.feetfit.server.repository.ShoeLabMeasurementRepository;
import com.feetfit.server.repository.ShoeLabMetricRepository;
import com.feetfit.server.repository.ShoeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoeRecommendationEligibilityServiceTest {

    @Mock ShoeRepository shoeRepository;
    @Mock ShoeLabMeasurementRepository measurementRepository;
    @Mock ShoeLabMetricRepository metricRepository;

    @Test
    void requiresOneUsableMetricFromEveryAiScoreDomainOnLatestSnapshots() {
        ShoeRecommendationEligibilityService service = service();
        ShoeLabMeasurement eligible = measurement(20L, shoe(2L));
        ShoeLabMeasurement missingBreathability = measurement(3340L, shoe(334L));
        ShoeLabMeasurement missingHeel = measurement(30L, shoe(3L));
        ShoeLabMeasurement missingForefoot = measurement(40L, shoe(4L));
        ShoeLabMeasurement nullBreathability = measurement(50L, shoe(5L));
        when(measurementRepository.findLatestBySource("RUNREPEAT")).thenReturn(List.of(
                missingBreathability, nullBreathability, eligible, missingHeel, missingForefoot));
        when(metricRepository.findByLabMeasurementIdInOrderByIdAsc(
                List.of(3340L, 50L, 20L, 30L, 40L))).thenReturn(List.of(
                metric(eligible, ShoeLabCharacteristic.TOEBOX_SPACE, "1"),
                metric(eligible, ShoeLabCharacteristic.CUSHION, "2"),
                metric(eligible, ShoeLabCharacteristic.BREATHABILITY, "3"),
                metric(missingBreathability, ShoeLabCharacteristic.WIDTH_SPACE, "1"),
                metric(missingBreathability, ShoeLabCharacteristic.SHOCK_ABSORPTION, "2"),
                metric(missingHeel, ShoeLabCharacteristic.WIDTH_SPACE, "1"),
                metric(missingHeel, ShoeLabCharacteristic.BREATHABILITY, "3"),
                metric(missingForefoot, ShoeLabCharacteristic.ENERGY_RETURN, "2"),
                metric(missingForefoot, ShoeLabCharacteristic.BREATHABILITY, "3"),
                metric(nullBreathability, ShoeLabCharacteristic.WIDTH_SPACE, "1"),
                metric(nullBreathability, ShoeLabCharacteristic.CUSHION, "2"),
                metric(nullBreathability, ShoeLabCharacteristic.BREATHABILITY, null)));

        assertThat(service.findEligibleShoeIds()).containsExactly(2L);
    }

    @Test
    void pagesTheSameSortedEligibleIdsUsedForTheExpectedCount() {
        ShoeRecommendationEligibilityService service = service();
        Shoe shoeThirty = shoe(30L);
        Shoe shoeTen = shoe(10L);
        Shoe shoeTwenty = shoe(20L);
        ShoeLabMeasurement measurementThirty = measurement(300L, shoeThirty);
        ShoeLabMeasurement measurementTen = measurement(100L, shoeTen);
        ShoeLabMeasurement measurementTwenty = measurement(200L, shoeTwenty);
        List<ShoeLabMeasurement> latest = List.of(
                measurementThirty, measurementTen, measurementTwenty);
        when(measurementRepository.findLatestBySource("RUNREPEAT")).thenReturn(latest);
        when(metricRepository.findByLabMeasurementIdInOrderByIdAsc(List.of(300L, 100L, 200L)))
                .thenReturn(completeMetrics(latest));
        when(shoeRepository.findAllById(List.of(10L, 20L)))
                .thenReturn(List.of(shoeTwenty, shoeTen));

        Page<Shoe> page = service.findEligibleShoes(PageRequest.of(0, 2));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.getContent()).extracting(Shoe::getId).containsExactly(10L, 20L);
        verify(shoeRepository).findAllById(List.of(10L, 20L));
    }

    private ShoeRecommendationEligibilityService service() {
        return new ShoeRecommendationEligibilityService(
                shoeRepository, measurementRepository, metricRepository);
    }

    private static List<ShoeLabMetric> completeMetrics(List<ShoeLabMeasurement> measurements) {
        List<ShoeLabMetric> metrics = new ArrayList<>();
        for (ShoeLabMeasurement measurement : measurements) {
            metrics.add(metric(measurement, ShoeLabCharacteristic.WIDTH_SPACE, "1"));
            metrics.add(metric(measurement, ShoeLabCharacteristic.ENERGY_RETURN, "2"));
            metrics.add(metric(measurement, ShoeLabCharacteristic.BREATHABILITY, "3"));
        }
        return metrics;
    }

    private static Shoe shoe(Long id) {
        return Shoe.builder()
                .id(id)
                .brandName("brand-" + id)
                .shoeName("shoe-" + id)
                .modelCode("model-" + id)
                .musinsaGoodsNo("goods-" + id)
                .musinsaUrl("https://example.com/" + id)
                .build();
    }

    private static ShoeLabMeasurement measurement(Long id, Shoe shoe) {
        return ShoeLabMeasurement.builder()
                .id(id)
                .shoe(shoe)
                .source("RUNREPEAT")
                .sourceUrl("https://runrepeat.com/" + id)
                .build();
    }

    private static ShoeLabMetric metric(
            ShoeLabMeasurement measurement,
            ShoeLabCharacteristic characteristic,
            String value) {
        return ShoeLabMetric.builder()
                .id(measurement.getId() * 10 + characteristic.ordinal())
                .labMeasurement(measurement)
                .canonicalCharacteristic(characteristic)
                .sourceMetricName(characteristic.name())
                .value(value == null ? null : new BigDecimal(value))
                .build();
    }
}
