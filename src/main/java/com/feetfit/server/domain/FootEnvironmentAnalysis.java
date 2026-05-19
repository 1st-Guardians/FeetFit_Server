package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import com.feetfit.server.domain.enums.EnvironmentScore;
import com.feetfit.server.domain.enums.FootSide;
import com.feetfit.server.domain.enums.GaugeStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "foot_environment_analysis")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class FootEnvironmentAnalysis extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "measurement_session_id", nullable = false)
    private MeasurementSession measurementSession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FootSide footSide;

    @Column(nullable = false)
    private Float avgTemperatureCelsius;

    @Column(nullable = false)
    private Float avgHumidityPercent;

    @Column(nullable = false)
    private Float avgOdorValue;

    @Column(nullable = false)
    private Float temperatureScore;

    @Column(nullable = false)
    private Float humidityScore;

    @Column(nullable = false)
    private Float odorScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnvironmentScore environmentScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GaugeStatus temperatureGaugeStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GaugeStatus humidityGaugeStatus;

    @Column(columnDefinition = "TEXT")
    private String analysisText;

    @Column(nullable = false)
    private LocalDateTime recordedAt;
}
