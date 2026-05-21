package com.feetfit.server.converter;

import com.feetfit.server.domain.Shoe;
import com.feetfit.server.domain.ShoeSearchHistory;
import com.feetfit.server.domain.User;
import com.feetfit.server.web.dto.shoe.ShoeSearchResponseDTO;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class ShoeSearchConverter {

    public static ShoeSearchResponseDTO.ShoeSearchItemDTO toShoeSearchItemDTO(Shoe shoe) {
        return ShoeSearchResponseDTO.ShoeSearchItemDTO.builder()
                .id(shoe.getId())
                .brandName(shoe.getBrandName())
                .shoeName(shoe.getShoeName())
                .price(shoe.getPrice())
                .imageUrl(shoe.getImageUrl())
                .overallRating(shoe.getOverallRating())
                .build();
    }

    public static ShoeSearchResponseDTO.ShoeSearchResultDTO toShoeSearchResultDTO(Page<Shoe> shoePage) {
        return ShoeSearchResponseDTO.ShoeSearchResultDTO.builder()
                .results(shoePage.getContent().stream()
                        .map(ShoeSearchConverter::toShoeSearchItemDTO)
                        .collect(Collectors.toList()))
                .currentPage(shoePage.getNumber())
                .totalPages(shoePage.getTotalPages())
                .totalElements(shoePage.getTotalElements())
                .hasNext(shoePage.hasNext())
                .build();
    }

    public static ShoeSearchHistory toShoeSearchHistory(User user, String keyword) {
        LocalDateTime now = LocalDateTime.now();
        return ShoeSearchHistory.builder()
                .user(user)
                .keyword(keyword)
                .searchedAt(now)
                .expiresAt(now.plusWeeks(1))
                .build();
    }

    public static ShoeSearchResponseDTO.ShoeSearchHistoryItemDTO toShoeSearchHistoryItemDTO(
            ShoeSearchHistory history) {
        return ShoeSearchResponseDTO.ShoeSearchHistoryItemDTO.builder()
                .id(history.getId())
                .keyword(history.getKeyword())
                .build();
    }

    public static ShoeSearchResponseDTO.ShoeSearchHistoryResultDTO toShoeSearchHistoryResultDTO(
            List<ShoeSearchHistory> histories) {
        return ShoeSearchResponseDTO.ShoeSearchHistoryResultDTO.builder()
                .histories(histories.stream()
                        .map(ShoeSearchConverter::toShoeSearchHistoryItemDTO)
                        .collect(Collectors.toList()))
                .build();
    }
}