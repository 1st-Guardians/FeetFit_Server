package com.feetfit.server.service.ShoeService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.ShoeHandler;
import com.feetfit.server.apiPayload.exception.handler.UserHandler;
import com.feetfit.server.converter.ShoeConverter;
import com.feetfit.server.domain.Shoe;
import com.feetfit.server.domain.ShoeClickHistory;
import com.feetfit.server.domain.ShoeRecommendation;
import com.feetfit.server.domain.ShoeRecommendationReason;
import com.feetfit.server.domain.ShoeReview;
import com.feetfit.server.domain.User;
import com.feetfit.server.repository.ShoeClickHistoryRepository;
import com.feetfit.server.repository.ShoeRecommendationReasonRepository;
import com.feetfit.server.repository.ShoeRecommendationReasonReviewRepository;
import com.feetfit.server.repository.ShoeRecommendationRepository;
import com.feetfit.server.repository.ShoeRepository;
import com.feetfit.server.repository.ShoeReviewRepository;
import com.feetfit.server.repository.UserRepository;
import com.feetfit.server.web.dto.shoe.ShoeRequestDTO;
import com.feetfit.server.web.dto.shoe.ShoeResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ShoeCommandServiceImpl implements ShoeCommandService {

    private final ShoeRepository shoeRepository;
    private final ShoeClickHistoryRepository shoeClickHistoryRepository;
    private final UserRepository userRepository;
    private final ShoeRecommendationRepository shoeRecommendationRepository;
    private final ShoeRecommendationReasonRepository shoeRecommendationReasonRepository;
    private final ShoeRecommendationReasonReviewRepository shoeRecommendationReasonReviewRepository;
    private final ShoeReviewRepository shoeReviewRepository;

    @Override
    public ShoeResponseDTO.ShoeClickResultDTO clickShoe(Long userId, Long shoeId) {

        Shoe shoe = shoeRepository.findById(shoeId)
                .orElseThrow(() -> new ShoeHandler(ErrorStatus.SHOE_NOT_FOUND));

        // 이미 클릭한 유저면 그냥 반환
        if (shoeClickHistoryRepository.existsByUserIdAndShoeId(userId, shoeId)) {
            return ShoeConverter.toShoeClickResultDTO(shoe);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));

        shoeClickHistoryRepository.save(
                ShoeClickHistory.builder()
                        .user(user)
                        .shoe(shoe)
                        .build()
        );
        shoeRepository.incrementClickCount(shoeId);

        Shoe updatedShoe = shoeRepository.findById(shoeId)
                .orElseThrow(() -> new ShoeHandler(ErrorStatus.SHOE_NOT_FOUND));

        return ShoeConverter.toShoeClickResultDTO(updatedShoe);
    }

    @Override
    public ShoeResponseDTO.SaveShoeRecommendationResultDTO saveShoeRecommendations(
            Long userId, ShoeRequestDTO.SaveShoeRecommendationDTO request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));

        LocalDateTime analyzedAt = LocalDateTime.now();
        List<Long> skippedShoeIds = new ArrayList<>();
        int processedCount = 0;

        for (ShoeRequestDTO.ShoeRecommendationItemDTO item : request.getRecommendations()) {
            Shoe shoe = shoeRepository.findById(item.getShoeId()).orElse(null);
            if (shoe == null) {
                skippedShoeIds.add(item.getShoeId());
                continue;
            }

            ShoeRecommendation recommendation = shoeRecommendationRepository
                    .findByUserIdAndShoeId(user.getId(), shoe.getId())
                    .map(existing -> {
                        existing.updateShoeRecommendation(item.getFitScore(), item.getPointSummary(), analyzedAt);
                        return existing;
                    })
                    .orElseGet(() -> shoeRecommendationRepository.save(
                            ShoeConverter.toNewShoeRecommendation(user, shoe, item, analyzedAt)));

            // 기존 부위별 근거를 통째로 교체. cascade/orphanRemoval에 기대면 reason마다
            // reasonReviews 컬렉션을 건별로 SELECT+DELETE하게 되어 느려서, 일괄 DELETE로 처리한다.
            List<Long> existingReasonIds = shoeRecommendationReasonRepository
                    .findByShoeRecommendationId(recommendation.getId())
                    .stream()
                    .map(ShoeRecommendationReason::getId)
                    .collect(Collectors.toList());
            if (!existingReasonIds.isEmpty()) {
                shoeRecommendationReasonReviewRepository.deleteByReasonIdIn(existingReasonIds);
                shoeRecommendationReasonRepository.deleteAllByIdInBatch(existingReasonIds);
            }

            // reason마다 따로 조회하지 않고, 이 신발의 reasons에 쓰인 reviewId 전체를 한 번에 조회/검증
            List<Long> allReviewIds = item.getReasons().stream()
                    .flatMap(reasonDto -> reasonDto.getReviewIds().stream())
                    .distinct()
                    .collect(Collectors.toList());
            Map<Long, ShoeReview> reviewsById = findReviewsBelongingToShoe(shoe, allReviewIds);

            for (ShoeRequestDTO.ShoeRecommendationReasonDTO reasonDto : item.getReasons()) {
                ShoeRecommendationReason savedReason = shoeRecommendationReasonRepository.save(
                        ShoeConverter.toShoeRecommendationReason(recommendation, reasonDto));

                shoeRecommendationReasonReviewRepository.saveAll(
                        reasonDto.getReviewIds().stream()
                                .map(reviewId -> ShoeConverter.toShoeRecommendationReasonReview(
                                        savedReason, reviewsById.get(reviewId)))
                                .collect(Collectors.toList())
                );
            }

            processedCount++;
        }

        log.info(
                "Shoe recommendations updated. measurementSessionId={}, userId={}, requested={}, processed={}, skipped={}",
                request.getMeasurementSessionId(), user.getId(),
                request.getRecommendations().size(), processedCount, skippedShoeIds
        );

        return ShoeConverter.toSaveShoeRecommendationResultDTO(
                request.getRecommendations().size(), processedCount, skippedShoeIds);
    }

    // reviewIds가 모두 해당 shoe에 속한 리뷰인지 검증하고, id -> ShoeReview 맵으로 반환.
    // 존재하지 않거나 다른 신발의 리뷰가 섞여 있으면 예외 발생
    private Map<Long, ShoeReview> findReviewsBelongingToShoe(Shoe shoe, List<Long> reviewIds) {
        Map<Long, ShoeReview> reviewsById = shoeReviewRepository.findAllById(reviewIds).stream()
                .collect(Collectors.toMap(ShoeReview::getId, review -> review));

        List<Long> invalidReviewIds = reviewIds.stream()
                .filter(reviewId -> {
                    ShoeReview review = reviewsById.get(reviewId);
                    return review == null || !review.getShoe().getId().equals(shoe.getId());
                })
                .collect(Collectors.toList());

        if (!invalidReviewIds.isEmpty()) {
            throw new ShoeHandler(
                    ErrorStatus.SHOE_REVIEW_MISMATCH,
                    String.format(
                            "shoeId=%d의 리뷰가 아니거나 존재하지 않는 reviewId입니다: %s",
                            shoe.getId(), invalidReviewIds
                    )
            );
        }

        return reviewsById;
    }
}