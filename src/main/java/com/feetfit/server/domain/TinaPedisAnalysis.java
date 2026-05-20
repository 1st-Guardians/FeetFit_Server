package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tina_pedis_analyses")
@AttributeOverride(name = "createdAt", column = @Column(name = "created_at", nullable = false))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TinaPedisAnalysis extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tina_pedis_analysis_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "measurement_id", nullable = false, unique = true)
    private MeasurementSession measurementSession;

    @Column(nullable = false)
    private Integer fungalSuspicionSafetyScore;

    @Column(nullable = false)
    private Integer skinReactionSafetyScore;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String fungalSuspicionSafetyDescription;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String skinReactionSafetyDescription;

    @Column(nullable = false)
    private String totalScoreDescription;

    @Column(nullable = false)
    private String suspiciousAreaMapImageUrl;

    @Column(nullable = false)
    private String originalFootImageUrl;

    @Column(nullable = false)
    private LocalDateTime recordedAt;

    public void updateTinaPedisAnalysis(
            Integer fungalSuspicionSafetyScore,
            Integer skinReactionSafetyScore,
            String fungalSuspicionSafetyDescription,
            String skinReactionSafetyDescription,
            String totalScoreDescription,
            String suspiciousAreaMapImageUrl,
            String originalFootImageUrl,
            LocalDateTime recordedAt
    ) {
        this.fungalSuspicionSafetyScore = fungalSuspicionSafetyScore;
        this.skinReactionSafetyScore = skinReactionSafetyScore;
        this.fungalSuspicionSafetyDescription = fungalSuspicionSafetyDescription;
        this.skinReactionSafetyDescription = skinReactionSafetyDescription;
        this.totalScoreDescription = totalScoreDescription;
        this.suspiciousAreaMapImageUrl = suspiciousAreaMapImageUrl;
        this.originalFootImageUrl = originalFootImageUrl;
        this.recordedAt = recordedAt;
    }
}
