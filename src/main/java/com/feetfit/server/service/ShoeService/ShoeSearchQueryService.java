package com.feetfit.server.service.ShoeService;

import com.feetfit.server.web.dto.shoe.ShoeResponseDTO;
import com.feetfit.server.web.dto.shoe.ShoeSearchResponseDTO;

public interface ShoeSearchQueryService {
    ShoeSearchResponseDTO.ShoeSearchResultDTO search(Long userId, String keyword, int page, int size);
    ShoeSearchResponseDTO.ShoeSearchHistoryResultDTO getSearchHistory(Long userId);
    void deleteSearchHistory(Long userId, Long historyId);
    ShoeResponseDTO.ShoeDetailResultDTO getShoeDetail(Long userId, Long shoeId);
}
