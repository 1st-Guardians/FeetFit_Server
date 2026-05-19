package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import com.feetfit.server.domain.enums.FootSide;
import com.feetfit.server.domain.enums.GaugeStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "static_pressure_analysis")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class StaticPressureAnalysis extends BaseEntity {

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
    private Float leftPressureRatio;

    @Column
    private Float rightPressureRatio;

    @Column
    private Float forefootPressureRatio;

    @Column
    private Float rearfootPressureRatio;

    @Column(nullable = false)
    private Float centerOfPressureX;

    @Column(nullable = false)
    private Float centerOfPressureY;

    @Column(nullable = false)
    private Float balanceScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GaugeStatus balanceStatus;

    @Column(columnDefinition = "TEXT")
    private String analysisText;
}
