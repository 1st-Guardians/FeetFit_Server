package com.feetfit.server.converter;

import com.feetfit.server.domain.Shoe;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.ShoeRecommendation;
import com.feetfit.server.domain.ShoeRecommendationReason;
import com.feetfit.server.domain.ShoeRecommendationReasonReview;
import com.feetfit.server.domain.ShoeReview;
import com.feetfit.server.domain.User;
import com.feetfit.server.web.dto.shoe.ShoeRequestDTO;
import com.feetfit.server.web.dto.shoe.ShoeResponseDTO;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ShoeConverter {

    public static ShoeResponseDTO.ShoeItemDTO toShoeItemDTO(Shoe shoe, Float fitScore) {
        return ShoeResponseDTO.ShoeItemDTO.builder()
                .id(shoe.getId())
                .brandName(shoe.getBrandName())
                .shoeName(shoe.getShoeName())
                .modelCode(shoe.getModelCode())
                .musinsaUrl(shoe.getMusinsaUrl())
                .price(shoe.getPrice())
                .imageUrl(shoe.getImageUrl())
                .overallRating(shoe.getOverallRating())
                .clickCount(shoe.getClickCount())
                .reviewCount(shoe.getReviewCount())
                .fitScore(fitScore)
                .build();
    }

    public static ShoeResponseDTO.ShoeListResultDTO toShoeListResultDTO(
            Page<Shoe> shoePage, Map<Long, Float> fitScoreMap) {

        List<ShoeResponseDTO.ShoeItemDTO> shoes = shoePage.getContent().stream()
                .map(shoe -> toShoeItemDTO(shoe, fitScoreMap.get(shoe.getId())))
                .collect(Collectors.toList());

        return ShoeResponseDTO.ShoeListResultDTO.builder()
                .shoes(shoes)
                .currentPage(shoePage.getNumber())
                .totalPages(shoePage.getTotalPages())
                .totalElements(shoePage.getTotalElements())
                .hasNext(shoePage.hasNext())
                .build();
    }

    public static ShoeResponseDTO.ShoeClickResultDTO toShoeClickResultDTO(Shoe shoe) {
        return ShoeResponseDTO.ShoeClickResultDTO.builder()
                .id(shoe.getId())
                .clickCount(shoe.getClickCount())
                .build();
    }

    public static ShoeResponseDTO.ShoeRecommendTop3ItemDTO toShoeRecommendTop3ItemDTO(
            Shoe shoe, Float fitScore) {
        return ShoeResponseDTO.ShoeRecommendTop3ItemDTO.builder()
                .id(shoe.getId())
                .brandName(shoe.getBrandName())
                .shoeName(shoe.getShoeName())
                .modelCode(shoe.getModelCode())
                .musinsaUrl(shoe.getMusinsaUrl())
                .price(shoe.getPrice())
                .imageUrl(shoe.getImageUrl())
                .overallRating(shoe.getOverallRating())
                .fitScore(fitScore)
                .build();
    }

    public static ShoeResponseDTO.ShoeRecommendTop3ResultDTO toShoeRecommendTop3ResultDTO(
            List<Shoe> shoes, Map<Long, Float> fitScoreMap) {
        return ShoeResponseDTO.ShoeRecommendTop3ResultDTO.builder()
                .shoes(shoes.stream()
                        .map(shoe -> toShoeRecommendTop3ItemDTO(shoe, fitScoreMap.get(shoe.getId())))
                        .collect(Collectors.toList()))
                .build();
    }

    public static ShoeResponseDTO.ReasonResultDTO toReasonResultDTO(
            ShoeRecommendationReason reason) {

        List<String> reviewTexts = reason.getReasonReviews().stream()
                .map(rr -> rr.getReview().getReviewText())
                .collect(Collectors.toList());

        return ShoeResponseDTO.ReasonResultDTO.builder()
                .reasonType(reason.getReasonType())
                .title(reason.getTitle())
                .riskLevel(reason.getRiskLevel())
                .reviewSummary(reason.getReviewSummary())
                .reviewTexts(reviewTexts)
                .build();
    }

    public static ShoeResponseDTO.ShoeDetailResultDTO toShoeDetailResultDTO(
            Shoe shoe,
            ShoeRecommendation recommendation,
            List<ShoeResponseDTO.ReasonResultDTO> reasons) {
        return ShoeResponseDTO.ShoeDetailResultDTO.builder()
                .id(shoe.getId())
                .brandName(shoe.getBrandName())
                .shoeName(shoe.getShoeName())
                .modelCode(shoe.getModelCode())
                .musinsaUrl(shoe.getMusinsaUrl())
                .price(shoe.getPrice())
                .imageUrl(shoe.getImageUrl())
                .overallRating(shoe.getOverallRating())
                .clickCount(shoe.getClickCount())
                .reviewCount(shoe.getReviewCount())
                .fitScore(recommendation != null ? recommendation.getFitScore() : null)
                .pointSummary(recommendation != null ? recommendation.getPointSummary() : null)
                .reasons(reasons)
                .build();
    }

    public static ShoeRecommendation toNewShoeRecommendation(
            User user,
            Shoe shoe,
            ShoeRequestDTO.ShoeRecommendationItemDTO item,
            LocalDateTime analyzedAt,
            MeasurementSession measurementSession) {
        return ShoeRecommendation.builder()
                .user(user)
                .shoe(shoe)
                .measurementSession(measurementSession)
                .fitScore(item.getFitScore())
                .pointSummary(item.getPointSummary())
                .analyzedAt(analyzedAt)
                .build();
    }

    public static ShoeRecommendationReason toShoeRecommendationReason(
            ShoeRecommendation recommendation,
            ShoeRequestDTO.ShoeRecommendationReasonDTO reasonDto) {
        return ShoeRecommendationReason.builder()
                .shoeRecommendation(recommendation)
                .reasonType(reasonDto.getReasonType())
                .title(reasonDto.getTitle())
                .riskLevel(reasonDto.getRiskLevel())
                .reviewSummary(reasonDto.getReviewSummary())
                .build();
    }

    public static ShoeRecommendationReasonReview toShoeRecommendationReasonReview(
            ShoeRecommendationReason reason,
            ShoeReview review) {
        return ShoeRecommendationReasonReview.builder()
                .reason(reason)
                .review(review)
                .build();
    }

    public static ShoeResponseDTO.SaveShoeRecommendationResultDTO toSaveShoeRecommendationResultDTO(
            int requestedCount,
            int processedCount,
            List<Long> skippedShoeIds,
            List<Long> invalidReviewShoeIds) {
        return ShoeResponseDTO.SaveShoeRecommendationResultDTO.builder()
                .requestedCount(requestedCount)
                .processedCount(processedCount)
                .skippedShoeIds(skippedShoeIds)
                .invalidReviewShoeIds(invalidReviewShoeIds)
                .build();
    }
}
