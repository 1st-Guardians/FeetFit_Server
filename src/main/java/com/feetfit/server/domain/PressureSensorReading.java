package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import com.feetfit.server.domain.enums.FootSide;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pressure_sensor_reading")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PressureSensorReading extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "measurement_session_id", nullable = false)
    private MeasurementSession measurementSession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FootSide footSide;

    @Column
    private LocalDateTime recordedAt;

    @OneToMany(mappedBy = "pressureSensorReading", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PressureSensorValue> sensorValues = new ArrayList<>();

    public void addSensorValue(Integer sensorIndex, Float pressureValue) {
        sensorValues.add(PressureSensorValue.builder()
                .pressureSensorReading(this)
                .sensorIndex(sensorIndex)
                .pressureValue(pressureValue)
                .build());
    }
}
