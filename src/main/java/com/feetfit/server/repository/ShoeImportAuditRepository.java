package com.feetfit.server.repository;

import com.feetfit.server.domain.ShoeImportAudit;
import com.feetfit.server.domain.enums.ShoeImportMatchStatus;
import com.feetfit.server.domain.enums.ShoeImportSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShoeImportAuditRepository extends JpaRepository<ShoeImportAudit, Long> {
    Page<ShoeImportAudit> findBySource(ShoeImportSource source, Pageable pageable);

    Page<ShoeImportAudit> findByMatchStatus(ShoeImportMatchStatus matchStatus, Pageable pageable);

    Page<ShoeImportAudit> findBySourceAndMatchStatus(
            ShoeImportSource source, ShoeImportMatchStatus matchStatus, Pageable pageable);
}
