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
     * Reads candidates from each shoe's latest source snapshot.
     *
     * String-shape compatibility is intentionally evaluated in Java. Applying
     * lower/trim/coalesce to columns and comparing those expressions with JDBC
     * string parameters is not portable across MySQL schemas whose historical
     * columns use different utf8mb4 collations.
     */
    @Query("""
            select metric
            from ShoeLabMetric metric
            join fetch metric.labMeasurement measurement
            where measurement.source = :source
              and metric.canonicalCharacteristic = :characteristic
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
    List<ShoeLabMetric> findLatestMetricsBySourceAndCharacteristic(
            @Param("source") String source,
            @Param("characteristic") ShoeLabCharacteristic characteristic);
}
