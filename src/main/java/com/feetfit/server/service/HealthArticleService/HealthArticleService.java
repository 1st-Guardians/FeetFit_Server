package com.feetfit.server.service.HealthArticleService;

import com.feetfit.server.web.dto.health.HealthArticleResponseDTO;

public interface HealthArticleService {

    HealthArticleResponseDTO.HealthArticleListResponseDTO getMyHealthArticles(Long userId);
}
