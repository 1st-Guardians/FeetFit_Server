package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "measurement_analysis_status",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_measurement_analysis_status_session",
                columnNames = "measurement_session_id"
        )
)
public class MeasurementAnalysisStatus extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "measurement_session_id", nullable = false, unique = true)
    private MeasurementSession measurementSession;

    @Builder.Default
    @Column(nullable = false)
    private boolean photoCaptureCompleted = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean photoAnalysisCompleted = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean pressureCaptureCompleted = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean pressureAnalysisCompleted = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean environmentAnalysisCompleted = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean metricReportCompleted = false;

    public void completePhotoCapture() {
        this.photoCaptureCompleted = true;
    }

    public void completePhotoAnalysis() {
        this.photoAnalysisCompleted = true;
    }

    public void completePressureCapture() {
        this.pressureCaptureCompleted = true;
    }

    public void completePressureAnalysis() {
        this.pressureAnalysisCompleted = true;
    }

    public void completeEnvironmentAnalysis() {
        this.environmentAnalysisCompleted = true;
    }

    public void completeMetricReport() {
        this.metricReportCompleted = true;
    }

    public boolean isReadyToComplete() {
        return photoCaptureCompleted
                && photoAnalysisCompleted
                && pressureCaptureCompleted
                && pressureAnalysisCompleted
                && environmentAnalysisCompleted
                && metricReportCompleted;
    }
}
