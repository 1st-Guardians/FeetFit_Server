package com.feetfit.server.repository;

import com.feetfit.server.domain.ShoeLabMetric;
import com.feetfit.server.domain.enums.ShoeLabCharacteristic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShoeLabMetricRepository extends JpaRepository<ShoeLabMetric, Long> {
    List<ShoeLabMetric> findByLabMeasurementIdInOrderByIdAsc(List<Long> measurementIds);

    List<ShoeLabMetric> findByLabMeasurementIdOrderByIdAsc(Long measurementId);

    /**
     * Reads only one compatible cohort from each shoe's latest RunRepeat
     * snapshot. Final source-name and duplicate checks remain in the service.
     */
    @Query("""
            select metric
            from ShoeLabMetric metric
            join fetch metric.labMeasurement measurement
            where measurement.source = :source
              and metric.canonicalCharacteristic = :characteristic
              and lower(trim(coalesce(metric.unit, ''))) = :unit
              and lower(trim(coalesce(metric.methodName, ''))) = :methodName
              and lower(trim(coalesce(metric.methodVersion, ''))) = :methodVersion
              and lower(trim(coalesce(metric.location, ''))) = :location
              and lower(trim(coalesce(metric.variant, ''))) = :variant
              and lower(trim(metric.comparisonCohort)) = :comparisonCohort
              and (:testedSize = '' or
                   lower(trim(coalesce(metric.testedSize, measurement.testedSize, ''))) = :testedSize)
              and not exists (
                select newer.id
                from ShoeLabMeasurement newer
                where newer.shoe = measurement.shoe
                  and newer.source = :source
                  and (
                    (measurement.capturedAt is null and newer.capturedAt is not null)
                    or (measurement.capturedAt is not null
                        and newer.capturedAt is not null
                        and newer.capturedAt > measurement.capturedAt)
                    or (((measurement.capturedAt is null and newer.capturedAt is null)
                         or newer.capturedAt = measurement.capturedAt)
                        and newer.id > measurement.id)
                  )
              )
            order by measurement.id asc, metric.id asc
            """)
    List<ShoeLabMetric> findLatestCompatibleMetrics(
            @Param("source") String source,
            @Param("characteristic") ShoeLabCharacteristic characteristic,
            @Param("unit") String unit,
            @Param("methodName") String methodName,
            @Param("methodVersion") String methodVersion,
            @Param("location") String location,
            @Param("variant") String variant,
            @Param("comparisonCohort") String comparisonCohort,
            @Param("testedSize") String testedSize);
}
