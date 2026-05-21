package com.feetfit.server.service.ShoeService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.ShoeHandler;
import com.feetfit.server.converter.ShoeConverter;
import com.feetfit.server.domain.Shoe;
import com.feetfit.server.domain.ShoeRecommendation;
import com.feetfit.server.domain.enums.ShoeSort;
import com.feetfit.server.repository.ShoeRecommendationRepository;
import com.feetfit.server.repository.ShoeRepository;
import com.feetfit.server.web.dto.shoe.ShoeRequestDTO;
import com.feetfit.server.web.dto.shoe.ShoeResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ShoeQueryServiceImpl implements ShoeQueryService {

    private final ShoeRepository shoeRepository;
    private final ShoeRecommendationRepository shoeRecommendationRepository;

    @Override
    public ShoeResponseDTO.ShoeListResultDTO getShoeList(Long userId, ShoeRequestDTO.ShoeListRequestDTO request) {

        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
        boolean hasMeasurement = shoeRecommendationRepository.existsByUserId(userId);

        // sort null 방어
        ShoeSort sort = request.getSort() != null ? request.getSort() : ShoeSort.FIT_SCORE;

        // 측정 안 한 유저가 발 적합도순 요청 시 400 에러
        if (sort == ShoeSort.FIT_SCORE && !hasMeasurement) {
            throw new ShoeHandler(ErrorStatus.SHOE_FIT_SCORE_UNAVAILABLE);
        }

        Page<Shoe> shoePage;
        Map<Long, Float> fitScoreMap = Map.of();

        switch (sort) {
            case FIT_SCORE -> {
                shoePage = shoeRepository.findAllByFitScoreDesc(userId, pageable);

                // 현재 페이지 shoeId 범위로만 fitScore 조회
                List<Long> shoeIds = shoePage.getContent().stream()
                        .map(Shoe::getId)
                        .collect(Collectors.toList());

                fitScoreMap = shoeRecommendationRepository
                        .findByUserIdAndShoeIdIn(userId, shoeIds).stream()
                        .collect(Collectors.toMap(
                                r -> r.getShoe().getId(),
                                ShoeRecommendation::getFitScore
                        ));
            }
            case RATING -> shoePage = shoeRepository.findAllByOrderByOverallRatingDesc(pageable);
            case CLICK -> shoePage = shoeRepository.findAllByOrderByClickCountDesc(pageable);
            default -> shoePage = shoeRepository.findAllByOrderByOverallRatingDesc(pageable);
        }

        Map<Long, Float> finalFitScoreMap = fitScoreMap;
        return ShoeConverter.toShoeListResultDTO(shoePage, finalFitScoreMap);
    }
}