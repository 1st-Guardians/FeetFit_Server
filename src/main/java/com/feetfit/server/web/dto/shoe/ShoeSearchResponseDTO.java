package com.feetfit.server.web.dto.shoe;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class ShoeSearchResponseDTO {

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShoeSearchResultDTO {
        private List<ShoeSearchItemDTO> results;
        private int currentPage;
        private int totalPages;
        private long totalElements;
        private boolean hasNext;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShoeSearchItemDTO {
        private Long id;
        private String brandName;
        private String shoeName;
        private Integer price;
        private String imageUrl;
        private Float overallRating;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShoeSearchHistoryResultDTO {
        private List<ShoeSearchHistoryItemDTO> histories;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShoeSearchHistoryItemDTO {
        private Long id;
        private String keyword;
    }
}