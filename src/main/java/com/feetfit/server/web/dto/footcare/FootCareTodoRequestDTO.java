package com.feetfit.server.web.dto.footcare;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class FootCareTodoRequestDTO {

    @Getter
    @NoArgsConstructor
    @Schema(description = "스트레칭 투두 완료 여부 변경 요청")
    public static class UpdateCompletionRequestDTO {
        @Schema(description = "완료 여부", example = "true")
        @NotNull(message = "완료 여부는 필수입니다.")
        private Boolean isCompleted;
    }
}
