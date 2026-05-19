package com.feetfit.server.web.dto.health;

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
    public static class HealthArticleInfoResponseDTO {
        private Long articleId;
        private String title;
        private String url;
        private String publisher;
        private LocalDateTime publishedAt;
        private String healthType;
        private String description;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthArticleListResponseDTO {
        private Integer totalCount;
        private List<HealthArticleInfoResponseDTO> articles;
    }
}
