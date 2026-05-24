package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import com.feetfit.server.domain.enums.GaugeStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "report")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Report extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "measurement_session_id", nullable = false)
    private MeasurementSession measurementSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime reportDate;

    @Column(nullable = false)
    private Integer totalScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GaugeStatus totalStatus;

    @Column(columnDefinition = "TEXT")
    private String summaryText;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL)
    @Builder.Default
    private List<ReportMetricScore> metricScores = new ArrayList<>();

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Advice> advices = new ArrayList<>();

}
