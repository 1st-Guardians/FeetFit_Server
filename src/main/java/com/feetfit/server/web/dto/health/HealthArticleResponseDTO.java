package com.feetfit.server.web.dto.health;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class HealthArticleResponseDTO {

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "건강 아티클 상세 응답")
    public static class HealthArticleInfoResponseDTO {
        @Schema(description = "건강 아티클 ID", example = "1")
        private Long articleId;

        @Schema(description = "아티클 제목", example = "무지외반 예방을 위한 발 스트레칭 방법")
        private String title;

        @Schema(description = "아티클 URL", example = "https://example.com/articles/hallux-valgus-stretching")
        private String url;

        @Schema(description = "발행처", example = "FeetFit Health")
        private String publisher;

        @Schema(description = "발행 시각", example = "2026-05-20T08:30:00")
        private LocalDateTime publishedAt;

        @Schema(description = "건강 타입", example = "HALLUX_VALGUS", allowableValues = {"ATHLETES_FOOT", "HALLUX_VALGUS", "FOOT_ODOR", "POSTURE", "FOOT_ENVIRONMENT"})
        private String healthType;

        @Schema(description = "아티클 설명", example = "엄지발가락 관절 부담을 줄이는 생활 습관과 스트레칭을 소개합니다.")
        private String description;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "건강 아티클 목록 응답")
    public static class HealthArticleListResponseDTO {
        @Schema(description = "조회된 아티클 개수", example = "4")
        private Integer totalCount;

        @Schema(description = "건강 아티클 목록")
        private List<HealthArticleInfoResponseDTO> articles;
    }
}
