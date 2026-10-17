package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "drug_recommendation")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DrugRecommendation extends BaseEntity {
    @Id
    private Long reportId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String prioritiesPayload;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false, length = 64)
    private String inputHash;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, length = 20)
    private String summarySource;

    private Instant generationStartedAt;
    private Instant generatedAt;

    public void prepare(String prioritiesPayload, String fallbackSummary, String inputHash, String model) {
        this.prioritiesPayload = prioritiesPayload;
        this.summary = fallbackSummary;
        this.inputHash = inputHash;
        this.model = model;
        this.status = "PENDING";
        this.summarySource = "RULE_BASED";
        this.generationStartedAt = null;
        this.generatedAt = null;
    }
}
