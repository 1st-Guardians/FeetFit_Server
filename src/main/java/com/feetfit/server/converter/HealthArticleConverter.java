package com.feetfit.server.converter;

import com.feetfit.server.domain.HealthArticle;
import com.feetfit.server.domain.UserHealthArticle;
import com.feetfit.server.web.dto.health.HealthArticleResponseDTO;

import java.util.List;

public class HealthArticleConverter {

    public static HealthArticleResponseDTO.HealthArticleInfoResponseDTO toHealthArticleInfoResponseDTO(
            UserHealthArticle userHealthArticle
    ) {
        HealthArticle healthArticle = userHealthArticle.getHealthArticle();

        return HealthArticleResponseDTO.HealthArticleInfoResponseDTO.builder()
                .articleId(healthArticle.getId())
                .title(healthArticle.getTitle())
                .url(healthArticle.getUrl())
                .publisher(healthArticle.getPublisher())
                .publishedAt(healthArticle.getPublishedAt())
                .healthType(healthArticle.getHealthType() == null ? null : healthArticle.getHealthType().name())
                .description(healthArticle.getDescription())
                .build();
    }

    public static HealthArticleResponseDTO.HealthArticleListResponseDTO toHealthArticleListResponseDTO(
            List<UserHealthArticle> userHealthArticles
    ) {
        List<HealthArticleResponseDTO.HealthArticleInfoResponseDTO> articles = userHealthArticles.stream()
                .map(HealthArticleConverter::toHealthArticleInfoResponseDTO)
                .toList();

        return HealthArticleResponseDTO.HealthArticleListResponseDTO.builder()
                .totalCount(articles.size())
                .articles(articles)
                .build();
    }
}
