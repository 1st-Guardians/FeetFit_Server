package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "measurement_session_id", nullable = false)
    private MeasurementSession measurementSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime reportDate;

    @Column(nullable = false)
    private Integer totalScore;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL)
    @Builder.Default
    private List<MetricAnalysisResult> metricAnalysisResults = new ArrayList<>();

    public void updateTotalScore(int totalScore) {
        this.totalScore = totalScore;
    }

    public void updateReportDate(LocalDateTime reportDate) {
        this.reportDate = reportDate;
    }
}
