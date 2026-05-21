package com.feetfit.server.web.dto.shoe;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class ShoeResponseDTO {

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShoeListResultDTO {
        private List<ShoeItemDTO> shoes;
        private int currentPage;
        private int totalPages;
        private long totalElements;
        private boolean hasNext;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShoeItemDTO {
        private Long id;
        private String brandName;
        private String shoeName;
        private String shoeUrl;
        private Integer price;
        private String imageUrl;
        private Float overallRating;
        private Integer clickCount;
        private Integer reviewCount;
        private Float fitScore;  // 측정 안 한 유저면 null
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShoeClickResultDTO {
        private Long id;
        private Integer clickCount;
    }

    // 추천 신발 3종
    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShoeRecommendTop3ResultDTO {
        private List<ShoeRecommendTop3ItemDTO> shoes;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShoeRecommendTop3ItemDTO {
        private Long id;
        private String brandName;
        private String shoeName;
        private Integer price;
        private String shoeUrl;
        private String imageUrl;
        private Float overallRating;
        private Float fitScore;
    }
}