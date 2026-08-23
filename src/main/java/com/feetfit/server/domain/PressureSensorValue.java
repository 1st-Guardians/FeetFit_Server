package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "pressure_sensor_value",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_pressure_sensor_value_reading_index",
                        columnNames = {"pressure_sensor_reading_id", "sensor_index"}
                )
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PressureSensorValue extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pressure_sensor_reading_id", nullable = false)
    private PressureSensorReading pressureSensorReading;

    @Column(name = "sensor_index", nullable = false)
    private Integer sensorIndex;

    @Column(name = "pressure_value", nullable = false)
    private Float pressureValue;
}
