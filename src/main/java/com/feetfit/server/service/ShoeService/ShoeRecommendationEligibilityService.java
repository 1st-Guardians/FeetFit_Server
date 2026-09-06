package com.feetfit.server.service.ShoeService;

import com.feetfit.server.domain.Shoe;
import com.feetfit.server.domain.ShoeLabMeasurement;
import com.feetfit.server.domain.ShoeLabMetric;
import com.feetfit.server.domain.enums.ShoeLabCharacteristic;
import com.feetfit.server.repository.ShoeLabMeasurementRepository;
import com.feetfit.server.repository.ShoeLabMetricRepository;
import com.feetfit.server.repository.ShoeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Single source of truth for the shoes that the AI fit policy can score.
 *
 * <p>Eligibility is evaluated only against each shoe's latest RunRepeat
 * snapshot. Reviews and older lab snapshots cannot fill a missing score domain.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShoeRecommendationEligibilityService {

    private static final String RUNREPEAT = "RUNREPEAT";
    private static final EnumSet<ShoeLabCharacteristic> FOREFOOT_METRICS = EnumSet.of(
            ShoeLabCharacteristic.WIDTH_SPACE,
            ShoeLabCharacteristic.TOEBOX_SPACE);
    private static final EnumSet<ShoeLabCharacteristic> HEEL_METRICS = EnumSet.of(
            ShoeLabCharacteristic.SHOCK_ABSORPTION,
            ShoeLabCharacteristic.ENERGY_RETURN,
            ShoeLabCharacteristic.CUSHION);

    private final ShoeRepository shoeRepository;
    private final ShoeLabMeasurementRepository shoeLabMeasurementRepository;
    private final ShoeLabMetricRepository shoeLabMetricRepository;

    public List<Long> findEligibleShoeIds() {
        List<ShoeLabMeasurement> latestMeasurements =
                shoeLabMeasurementRepository.findLatestBySource(RUNREPEAT);
        if (latestMeasurements.isEmpty()) {
            return List.of();
        }

        List<Long> measurementIds = latestMeasurements.stream()
                .map(ShoeLabMeasurement::getId)
                .toList();
        Map<Long, List<ShoeLabMetric>> metricsByMeasurement =
                shoeLabMetricRepository.findByLabMeasurementIdInOrderByIdAsc(measurementIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                metric -> metric.getLabMeasurement().getId()));

        return latestMeasurements.stream()
                .filter(measurement -> isEligible(
                        metricsByMeasurement.getOrDefault(measurement.getId(), List.of())))
                .map(measurement -> measurement.getShoe().getId())
                .distinct()
                .sorted()
                .toList();
    }

    public long countEligibleShoes() {
        return findEligibleShoeIds().size();
    }

    public Page<Shoe> findEligibleShoes(Pageable pageable) {
        List<Long> eligibleIds = findEligibleShoeIds();
        long offset = pageable.getOffset();
        if (offset >= eligibleIds.size()) {
            return new PageImpl<>(List.of(), pageable, eligibleIds.size());
        }

        int fromIndex = Math.toIntExact(offset);
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), eligibleIds.size());
        List<Long> pageIds = eligibleIds.subList(fromIndex, toIndex);
        Map<Long, Shoe> shoesById = shoeRepository.findAllById(pageIds).stream()
                .collect(Collectors.toMap(Shoe::getId, Function.identity()));
        List<Shoe> shoes = pageIds.stream()
                .map(shoesById::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        return new PageImpl<>(shoes, pageable, eligibleIds.size());
    }

    private static boolean isEligible(List<ShoeLabMetric> metrics) {
        if (metrics.isEmpty()) {
            return false;
        }
        EnumSet<ShoeLabCharacteristic> usable = metrics.stream()
                .filter(metric -> metric.getValue() != null)
                .map(ShoeLabMetric::getCanonicalCharacteristic)
                .collect(Collectors.toCollection(
                        () -> EnumSet.noneOf(ShoeLabCharacteristic.class)));
        return !Collections.disjoint(usable, FOREFOOT_METRICS)
                && !Collections.disjoint(usable, HEEL_METRICS)
                && usable.contains(ShoeLabCharacteristic.BREATHABILITY);
    }
}
