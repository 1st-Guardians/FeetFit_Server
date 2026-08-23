package com.feetfit.server.repository;

import com.feetfit.server.domain.ShoeLabMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShoeLabMeasurementRepository extends JpaRepository<ShoeLabMeasurement, Long> {
    Optional<ShoeLabMeasurement> findBySnapshotKey(String snapshotKey);

    List<ShoeLabMeasurement> findByShoeIdOrderByCapturedAtDescIdDesc(Long shoeId);

    List<ShoeLabMeasurement> findByShoeIdAndSourceOrderByCapturedAtDescIdDesc(
            Long shoeId, String source);

    List<ShoeLabMeasurement> findByShoeIdInAndSourceOrderByCapturedAtDescIdDesc(
            List<Long> shoeIds, String source);

    /** Latest source snapshot for one shoe, using the same null/tie policy as the cohort query. */
    @Query("""
            select measurement
            from ShoeLabMeasurement measurement
            join fetch measurement.shoe shoe
            where shoe.id = :shoeId
              and measurement.source = :source
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
            """)
    Optional<ShoeLabMeasurement> findLatestByShoeIdAndSource(
            @Param("shoeId") Long shoeId,
            @Param("source") String source);

    /**
     * Latest source snapshot per shoe. Newer capturedAt wins; id is the stable
     * tie-breaker. Legacy null capturedAt rows are used only when no dated row
     * exists for that shoe.
     */
    @Query("""
            select measurement
            from ShoeLabMeasurement measurement
            join fetch measurement.shoe shoe
            where measurement.source = :source
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
            order by measurement.id asc
            """)
    List<ShoeLabMeasurement> findLatestBySource(@Param("source") String source);
}
