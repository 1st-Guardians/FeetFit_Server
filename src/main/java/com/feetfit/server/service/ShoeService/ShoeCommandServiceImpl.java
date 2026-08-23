package com.feetfit.server.service.ShoeService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.ShoeHandler;
import com.feetfit.server.apiPayload.exception.handler.MeasurementHandler;
import com.feetfit.server.apiPayload.exception.handler.UserHandler;
import com.feetfit.server.converter.ShoeConverter;
import com.feetfit.server.domain.Shoe;
import com.feetfit.server.domain.MeasurementSession;
import com.feetfit.server.domain.ShoeClickHistory;
import com.feetfit.server.domain.ShoeRecommendation;
import com.feetfit.server.domain.ShoeRecommendationReason;
import com.feetfit.server.domain.ShoeReview;
import com.feetfit.server.domain.User;
import com.feetfit.server.domain.enums.MeasurementStatus;
import com.feetfit.server.domain.enums.ReasonType;
import com.feetfit.server.domain.enums.ShoeReviewSource;
import com.feetfit.server.repository.MeasurementSessionRepository;
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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final MeasurementSessionRepository measurementSessionRepository;

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

        validateRecommendationRequest(request);

        MeasurementSession measurementSession = measurementSessionRepository
                .findById(request.getMeasurementSessionId())
                .orElseThrow(() -> new MeasurementHandler(ErrorStatus.MEASUREMENT_NOT_FOUND));
        if (!measurementSession.getUser().getId().equals(userId)) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_FORBIDDEN);
        }
        if (measurementSession.getStatus() != MeasurementStatus.COMPLETED) {
            throw new MeasurementHandler(ErrorStatus.MEASUREMENT_NOT_COMPLETED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));

        LocalDateTime analyzedAt = LocalDateTime.now();
        List<Long> skippedShoeIds = new ArrayList<>();
        List<Long> invalidReviewShoeIds = new ArrayList<>();
        int processedCount = 0;

        for (ShoeRequestDTO.ShoeRecommendationItemDTO item : request.getRecommendations()) {
            Shoe shoe = shoeRepository.findById(item.getShoeId()).orElse(null);
            if (shoe == null) {
                skippedShoeIds.add(item.getShoeId());
                continue;
            }

            // 쓰기 작업을 시작하기 전에 먼저 검증한다 (shoeId 없음과 동일하게, 이 신발만 건너뛰고
            // 나머지 배치는 계속 처리하기 위함 — 검증 실패 시 아무 것도 쓰지 않은 상태라 안전하게 skip 가능)
            List<Long> allReviewIds = item.getReasons().stream()
                    .flatMap(reasonDto -> reasonDto.getReviewIds().stream())
                    .distinct()
                    .collect(Collectors.toList());
            Map<Long, ShoeReview> reviewsById;
            try {
                reviewsById = findReviewsBelongingToShoe(shoe, allReviewIds);
            } catch (ShoeHandler ex) {
                log.warn("Skipping shoeId={} due to invalid reviewIds: {}", shoe.getId(), ex.getMessage());
                invalidReviewShoeIds.add(shoe.getId());
                continue;
            }

            ShoeRecommendation recommendation = shoeRecommendationRepository
                    .findByUserIdAndShoeId(user.getId(), shoe.getId())
                    .map(existing -> {
                        existing.updateShoeRecommendation(
                                item.getFitScore(), item.getPointSummary(), analyzedAt, measurementSession);
                        return existing;
                    })
                    .orElseGet(() -> shoeRecommendationRepository.save(
                            ShoeConverter.toNewShoeRecommendation(
                                    user, shoe, item, analyzedAt, measurementSession)));

            // 아래에서 reasonReview/reason을 @Modifying 벌크 쿼리로 바로 지우기 때문에,
            // 위 upsert(특히 기존 엔티티의 updateShoeRecommendation)가 아직 flush 안 된 상태로 남아있으면
            // 곧이어 나오는 clearAutomatically=true에 의해 그 변경분이 유실될 수 있다. 먼저 flush로 반영해둔다.
            shoeRecommendationRepository.flush();

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
                "Shoe recommendations updated. measurementSessionId={}, userId={}, requested={}, processed={}, "
                        + "skipped={}, invalidReviewShoeIds={}",
                request.getMeasurementSessionId(), user.getId(),
                request.getRecommendations().size(), processedCount, skippedShoeIds, invalidReviewShoeIds
        );

        return ShoeConverter.toSaveShoeRecommendationResultDTO(
                request.getRecommendations().size(), processedCount, skippedShoeIds, invalidReviewShoeIds);
    }

    @Override
    public void saveShoeSummaries(
            Long userId, Long shoeId, ShoeRequestDTO.SaveShoeSummariesDTO request) {
        validateSummaryRequest(request);
        ShoeRecommendation recommendation = shoeRecommendationRepository
                .findByUserIdAndShoeId(userId, shoeId)
                .orElseThrow(() -> new ShoeHandler(ErrorStatus.SHOE_RECOMMENDATION_NOT_FOUND));

        recommendation.updatePointSummary(request.getPointSummary());
        for (ShoeRequestDTO.ShoeReasonSummaryDTO summary : request.getReasons()) {
            ShoeRecommendationReason reason = shoeRecommendationReasonRepository
                    .findByShoeRecommendationIdAndReasonType(
                            recommendation.getId(), summary.getReasonType())
                    .orElseThrow(() -> new ShoeHandler(
                            ErrorStatus.SHOE_RECOMMENDATION_NOT_FOUND,
                            "저장된 추천에 " + summary.getReasonType() + " 근거가 없습니다."));
            reason.updateReviewSummary(summary.getReviewSummary());
        }
    }

    private void validateRecommendationRequest(ShoeRequestDTO.SaveShoeRecommendationDTO request) {
        if (request == null || request.getMeasurementSessionId() == null
                || request.getRecommendations() == null || request.getRecommendations().isEmpty()) {
            throw invalidRecommendationRequest("measurementSessionId와 recommendations는 필수입니다.");
        }
        for (ShoeRequestDTO.ShoeRecommendationItemDTO item : request.getRecommendations()) {
            if (item == null || item.getShoeId() == null || item.getFitScore() == null
                    || !Float.isFinite(item.getFitScore())
                    || item.getFitScore() < 0.0f || item.getFitScore() > 100.0f) {
                throw invalidRecommendationRequest("shoeId와 0~100 범위의 fitScore가 필요합니다.");
            }
            validateReasons(item.getReasons());
            for (ShoeRequestDTO.ShoeRecommendationReasonDTO reason : item.getReasons()) {
                if (reason.getReviewIds() == null || reason.getReviewIds().size() > 3
                        || reason.getReviewIds().stream().anyMatch(java.util.Objects::isNull)
                        || new HashSet<>(reason.getReviewIds()).size() != reason.getReviewIds().size()) {
                    throw invalidRecommendationRequest(
                            "각 reason의 reviewIds는 중복 없이 최대 3개여야 합니다.");
                }
            }
        }
    }

    private void validateSummaryRequest(ShoeRequestDTO.SaveShoeSummariesDTO request) {
        if (request == null || request.getPointSummary() == null
                || request.getPointSummary().isBlank() || request.getReasons() == null) {
            throw invalidRecommendationRequest("pointSummary와 reasons는 필수입니다.");
        }
        if (request.getReasons().size() != 3) {
            throw invalidRecommendationRequest("요약 reasons는 정확히 3개여야 합니다.");
        }
        Set<ReasonType> types = EnumSet.noneOf(ReasonType.class);
        for (ShoeRequestDTO.ShoeReasonSummaryDTO reason : request.getReasons()) {
            if (reason == null || reason.getReasonType() == null
                    || reason.getReviewSummary() == null || reason.getReviewSummary().isBlank()
                    || !types.add(reason.getReasonType())) {
                throw invalidRecommendationRequest("요약 reasonType은 중복 없이 모두 존재해야 합니다.");
            }
        }
        if (!types.equals(EnumSet.allOf(ReasonType.class))) {
            throw invalidRecommendationRequest("FOREFOOT/HEEL/INSOLE 요약이 모두 필요합니다.");
        }
    }

    private void validateReasons(List<ShoeRequestDTO.ShoeRecommendationReasonDTO> reasons) {
        if (reasons == null || reasons.size() != 3) {
            throw invalidRecommendationRequest("reasons는 정확히 3개여야 합니다.");
        }
        Set<ReasonType> types = EnumSet.noneOf(ReasonType.class);
        for (ShoeRequestDTO.ShoeRecommendationReasonDTO reason : reasons) {
            if (reason == null || reason.getReasonType() == null || !types.add(reason.getReasonType())) {
                throw invalidRecommendationRequest("reasonType은 중복 없이 모두 존재해야 합니다.");
            }
        }
        if (!types.equals(EnumSet.allOf(ReasonType.class))) {
            throw invalidRecommendationRequest("FOREFOOT/HEEL/INSOLE reason이 모두 필요합니다.");
        }
    }

    private ShoeHandler invalidRecommendationRequest(String message) {
        return new ShoeHandler(ErrorStatus._BAD_REQUEST, message);
    }

    // reviewIds가 모두 해당 shoe에 속한 리뷰인지 검증하고, id -> ShoeReview 맵으로 반환.
    // 존재하지 않거나 다른 신발의 리뷰가 섞여 있으면 예외 발생 (호출부에서 캐치해 해당 신발만 skip 처리)
    private Map<Long, ShoeReview> findReviewsBelongingToShoe(Shoe shoe, List<Long> reviewIds) {
        Map<Long, ShoeReview> reviewsById = shoeReviewRepository.findAllById(reviewIds).stream()
                .collect(Collectors.toMap(ShoeReview::getId, review -> review));

        List<Long> invalidReviewIds = reviewIds.stream()
                .filter(reviewId -> {
                    ShoeReview review = reviewsById.get(reviewId);
                    return review == null
                            || !review.getShoe().getId().equals(shoe.getId())
                            || review.getSource() != ShoeReviewSource.MUSINSA;
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
