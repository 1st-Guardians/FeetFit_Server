package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import com.feetfit.server.domain.enums.FootSide;
import com.feetfit.server.domain.enums.OdorLevel;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "foot_odor_analysis")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class FootOdorAnalysis extends BaseEntity {

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
    @Column(nullable = false)
    private OdorLevel odorLevel;

    @Column(nullable = false)
    private Float riskScore;

    @Column(columnDefinition = "TEXT")
    private String analysisText;
}
