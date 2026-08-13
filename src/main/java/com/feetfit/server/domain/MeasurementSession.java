package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import com.feetfit.server.domain.enums.MeasurementStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "measurement_session")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MeasurementSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MeasurementStatus status = MeasurementStatus.PENDING;

    @Column
    private Integer measurementDurationSec;

    @Column(nullable = false)
    private LocalDateTime measuredAt;

    @OneToMany(mappedBy = "measurementSession", cascade = CascadeType.ALL)
    @Builder.Default
    private List<PressureSensorReading> pressureSensorReadings = new ArrayList<>();

    @OneToMany(mappedBy = "measurementSession", cascade = CascadeType.ALL)
    @Builder.Default
    private List<PlantarFootprint> plantarFootprints = new ArrayList<>();

    @OneToMany(mappedBy = "measurementSession", cascade = CascadeType.ALL)
    @Builder.Default
    private List<FootEnvironmentReading> footEnvironmentReadings = new ArrayList<>();

    @OneToMany(mappedBy = "measurementSession", cascade = CascadeType.ALL)
    @Builder.Default
    private List<FootOdorReading> footOdorReadings = new ArrayList<>();

    @OneToMany(mappedBy = "measurementSession", cascade = CascadeType.ALL)
    @Builder.Default
    private List<StaticPressureAnalysis> staticPressureAnalyses = new ArrayList<>();

    @OneToMany(mappedBy = "measurementSession", cascade = CascadeType.ALL)
    @Builder.Default
    private List<FootEnvironmentAnalysis> footEnvironmentAnalyses = new ArrayList<>();

    @OneToMany(mappedBy = "measurementSession", cascade = CascadeType.ALL)
    @Builder.Default
    private List<HalluxValgusAnalysis> halluxValgusAnalyses = new ArrayList<>();

    @OneToMany(mappedBy = "measurementSession", cascade = CascadeType.ALL)
    @Builder.Default
    private List<FootOdorAnalysis> footOdorAnalyses = new ArrayList<>();

    @OneToMany(mappedBy = "measurementSession")
    @Builder.Default
    private List<Report> reports = new ArrayList<>();

    @OneToOne(mappedBy = "measurementSession", cascade = CascadeType.ALL)
    private TinaPedisAnalysis tinaPedisAnalysis;

    public void updateStatus(MeasurementStatus status, Integer measurementDurationSec) {
        this.status = status;
        this.measurementDurationSec = measurementDurationSec;
    }
}
