package com.feetfit.server.repository;

import com.feetfit.server.domain.DrugRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface DrugRecommendationRepository extends JpaRepository<DrugRecommendation, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DrugRecommendation d set d.status = 'GENERATING', d.generationStartedAt = :now
            where d.reportId = :reportId and d.inputHash = :hash
              and (d.status in ('PENDING', 'FAILED')
                   or (d.status = 'GENERATING' and d.generationStartedAt < :expired))
            """)
    int claim(@Param("reportId") Long reportId, @Param("hash") String hash,
              @Param("now") Instant now, @Param("expired") Instant expired);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DrugRecommendation d
            set d.summary = :summary, d.status = 'COMPLETED', d.summarySource = 'GPT', d.generatedAt = :now
            where d.reportId = :reportId and d.inputHash = :hash and d.status = 'GENERATING'
            """)
    int complete(@Param("reportId") Long reportId, @Param("hash") String hash,
                 @Param("summary") String summary, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DrugRecommendation d set d.status = 'FAILED'
            where d.reportId = :reportId and d.inputHash = :hash and d.status = 'GENERATING'
            """)
    int fail(@Param("reportId") Long reportId, @Param("hash") String hash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DrugRecommendation d set d.status = 'FAILED'
            where d.reportId = :reportId and d.inputHash = :hash and d.status = 'PENDING'
            """)
    int failPending(@Param("reportId") Long reportId, @Param("hash") String hash);
}
