package com.feetfit.server.service.ShoeService;

import com.feetfit.server.web.dto.shoe.ShoeRequestDTO;
import com.feetfit.server.web.dto.shoe.ShoeResponseDTO;

public interface ShoeCommandService {
    ShoeResponseDTO.ShoeClickResultDTO clickShoe(Long userId, Long shoeId);

    // 사용자-신발 발 적합도 배치 생성/갱신 (Feetfit_AI가 사용자의 accessToken을 그대로 포워딩해 호출)
    ShoeResponseDTO.SaveShoeRecommendationResultDTO saveShoeRecommendations(
            Long userId, ShoeRequestDTO.SaveShoeRecommendationDTO request);

    void saveShoeSummaries(Long userId, Long shoeId, ShoeRequestDTO.SaveShoeSummariesDTO request);
}
