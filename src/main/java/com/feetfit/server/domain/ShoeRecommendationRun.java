package com.feetfit.server.domain;

import com.feetfit.server.domain.common.BaseEntity;
import com.feetfit.server.domain.enums.ShoeRecommendationRunStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "shoe_recommendation_run",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_shoe_recommendation_run_session",
                columnNames = "measurement_session_id"
        )
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ShoeRecommendationRun extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shoe_recommendation_run_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "measurement_session_id", nullable = false, unique = true)
    private MeasurementSession measurementSession;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private ShoeRecommendationRunStatus status = ShoeRecommendationRunStatus.PENDING;

    @Column(nullable = false)
    private Integer expectedCount;

    @Column(nullable = false)
    @Builder.Default
    private Integer processedCount = 0;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    @Column(columnDefinition = "TEXT")
    private String failureDetail;

    public void start(int expectedCount, boolean restartCompleted, LocalDateTime startedAt) {
        requirePositiveExpectedCount(expectedCount);
        if (status == ShoeRecommendationRunStatus.RUNNING) {
            if (!this.expectedCount.equals(expectedCount)) {
                throw new IllegalStateException("RUNNING 추천 실행의 expectedCount는 변경할 수 없습니다.");
            }
            return;
        }
        if (status == ShoeRecommendationRunStatus.COMPLETED && !restartCompleted) {
            throw new IllegalStateException("완료된 추천 실행을 재시작하려면 명시적인 restart가 필요합니다.");
        }
        this.status = ShoeRecommendationRunStatus.RUNNING;
        this.expectedCount = expectedCount;
        this.processedCount = 0;
        this.startedAt = startedAt;
        this.completedAt = null;
        this.failureDetail = null;
    }

    public void updateProcessedCount(long processedCount) {
        if (status != ShoeRecommendationRunStatus.RUNNING) {
            throw new IllegalStateException("RUNNING 추천 실행만 진행 수를 갱신할 수 있습니다.");
        }
        if (processedCount < 0 || processedCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("processedCount 범위가 올바르지 않습니다.");
        }
        this.processedCount = (int) processedCount;
    }

    public void complete(LocalDateTime completedAt) {
        if (status != ShoeRecommendationRunStatus.RUNNING) {
            throw new IllegalStateException("RUNNING 추천 실행만 완료할 수 있습니다.");
        }
        if (!processedCount.equals(expectedCount)) {
            throw new IllegalStateException("processedCount와 expectedCount가 일치해야 합니다.");
        }
        this.status = ShoeRecommendationRunStatus.COMPLETED;
        this.completedAt = completedAt;
        this.failureDetail = null;
    }

    public void fail(String failureDetail) {
        if (status != ShoeRecommendationRunStatus.PENDING
                && status != ShoeRecommendationRunStatus.RUNNING) {
            throw new IllegalStateException("PENDING 또는 RUNNING 추천 실행만 실패 처리할 수 있습니다.");
        }
        this.status = ShoeRecommendationRunStatus.FAILED;
        this.completedAt = null;
        this.failureDetail = failureDetail;
    }

    private static void requirePositiveExpectedCount(int expectedCount) {
        if (expectedCount <= 0) {
            throw new IllegalArgumentException("expectedCount는 양수여야 합니다.");
        }
    }
}
