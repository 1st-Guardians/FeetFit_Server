package com.feetfit.server.web.dto.shoe;

import com.feetfit.server.domain.ShoeRecommendationRun;
import com.feetfit.server.domain.enums.ShoeRecommendationRunStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

public class ShoeRecommendationRunResponseDTO {

    @Getter
    @Builder
    public static class RunResultDTO {
        private Long recommendationRunId;
        private Long measurementSessionId;
        private ShoeRecommendationRunStatus status;
        private Integer expectedCount;
        private Integer processedCount;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private String failureDetail;

        public static RunResultDTO from(ShoeRecommendationRun run) {
            return RunResultDTO.builder()
                    .recommendationRunId(run.getId())
                    .measurementSessionId(run.getMeasurementSession().getId())
                    .status(run.getStatus())
                    .expectedCount(run.getExpectedCount())
                    .processedCount(run.getProcessedCount())
                    .startedAt(run.getStartedAt())
                    .completedAt(run.getCompletedAt())
                    .failureDetail(run.getFailureDetail())
                    .build();
        }
    }
}
