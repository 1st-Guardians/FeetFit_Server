package com.feetfit.server.web.dto.shoe;

import com.feetfit.server.domain.enums.ReasonType;
import com.feetfit.server.domain.enums.RiskLevel;
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
        private Long measurementSessionId;
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
        private String modelCode;
        private String musinsaUrl;
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
        private Long measurementSessionId;
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
        private String modelCode;
        private Integer price;
        private String musinsaUrl;
        private String imageUrl;
        private Float overallRating;
        private Float fitScore;
    }

    // 신발 디테일 - 각 타입(발볼, 뒤꿈치, 깔창)
    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShoeDetailResultDTO {
        private Long measurementSessionId;
        private Long id;
        private String brandName;
        private String shoeName;
        private String modelCode;
        private String musinsaUrl;
        private Integer price;
        private String imageUrl;
        private Float overallRating;      // 별점
        private Integer clickCount;
        private Integer reviewCount;
        private Float fitScore;           // 적합도 % (없으면 null)
        private String pointSummary;     // 한 눈에 보는 착용 포인트 (없으면 null)
        private List<ReasonResultDTO> reasons;  // 부위별 정보 (없으면 빈 배열)
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReasonResultDTO {
        private ReasonType reasonType;    // FOREFOOT, HEEL, INSOLE
        private String title;             // 발볼 넓음, 뒤꿈치 까짐 주의, 깔창 얇음
        private RiskLevel riskLevel;      // LOW, MEDIUM, HIGH
        private String reviewSummary;     // 긴 멘트
        private List<String> reviewTexts; // AI가 선택한 리뷰 3개
    }

    // 발 적합도 배치 저장 결과
    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SaveShoeRecommendationResultDTO {
        private Long measurementSessionId;
        private Integer requestedCount;
        private Integer processedCount;
        private List<Long> skippedShoeIds;          // DB에 없는 shoeId라서 건너뜀
        private List<Long> invalidReviewShoeIds;     // reviewId가 해당 shoeId 소유가 아니라서 건너뜀
    }
}
