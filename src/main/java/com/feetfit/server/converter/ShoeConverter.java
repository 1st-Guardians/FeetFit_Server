package com.feetfit.server.converter;

import com.feetfit.server.domain.Shoe;
import com.feetfit.server.web.dto.shoe.ShoeResponseDTO;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ShoeConverter {

    public static ShoeResponseDTO.ShoeItemDTO toShoeItemDTO(Shoe shoe, Float fitScore) {
        return ShoeResponseDTO.ShoeItemDTO.builder()
                .id(shoe.getId())
                .brandName(shoe.getBrandName())
                .shoeName(shoe.getShoeName())
                .shoeUrl(shoe.getShoeUrl())
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
}