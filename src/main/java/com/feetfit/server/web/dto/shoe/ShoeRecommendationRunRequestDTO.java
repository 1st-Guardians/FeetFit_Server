package com.feetfit.server.web.dto.shoe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class ShoeRecommendationRunRequestDTO {

    @Getter
    @NoArgsConstructor
    public static class PrepareRunDTO {
        @NotNull
        @Positive
        private Integer expectedCount;
    }

    @Getter
    @NoArgsConstructor
    public static class StartRunDTO {
        @NotNull
        @Positive
        private Integer expectedCount;

        private boolean restartCompleted;
    }

    @Getter
    @NoArgsConstructor
    public static class FailRunDTO {
        @NotBlank
        private String failureDetail;
    }
}
