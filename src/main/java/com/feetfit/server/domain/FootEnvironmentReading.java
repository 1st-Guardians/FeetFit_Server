package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import com.feetfit.server.domain.enums.FootSide;
import com.feetfit.server.domain.enums.SensorPlacement;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "foot_environment_reading")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class FootEnvironmentReading extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "measurement_session_id", nullable = false)
    private MeasurementSession measurementSession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FootSide footSide;

    @Enumerated(EnumType.STRING)
    @Column
    private SensorPlacement sensorPlacement;

    @Column
    private Float temperatureCelsius;

    @Column
    private Float humidityPercent;

    @Column
    private LocalDateTime recordedAt;
}
