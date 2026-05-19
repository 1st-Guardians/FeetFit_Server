package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import com.feetfit.server.domain.enums.FootSide;
import com.feetfit.server.domain.enums.GaugeStatus;
import com.feetfit.server.domain.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "hallux_valgus_analysis")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class HalluxValgusAnalysis extends BaseEntity {

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
    private Float toeAngleDegree;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskLevel riskLevel;

    @Column(nullable = false)
    private Float riskScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GaugeStatus orderStatus;

    @Column(columnDefinition = "TEXT")
    private String analysisText;
}
