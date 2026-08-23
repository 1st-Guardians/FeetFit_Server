package com.feetfit.server.service.ShoeService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.ShoeHandler;
import com.feetfit.server.apiPayload.exception.handler.UserHandler;
import com.feetfit.server.converter.ShoeConverter;
import com.feetfit.server.converter.ShoeSearchConverter;
import com.feetfit.server.domain.*;
import com.feetfit.server.repository.*;
import com.feetfit.server.web.dto.shoe.ShoeResponseDTO;
import com.feetfit.server.web.dto.shoe.ShoeSearchResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class ShoeSearchQueryServiceImpl implements ShoeSearchQueryService {

    private final ShoeRepository shoeRepository;
    private final ShoeSearchHistoryRepository shoeSearchHistoryRepository;
    private final UserRepository userRepository;
    private final ShoeRecommendationReasonRepository shoeRecommendationReasonRepository;
    private final ShoeRecommendationRepository shoeRecommendationRepository;

    private static final int MAX_HISTORY_COUNT = 10;

    @Override
    public ShoeSearchResponseDTO.ShoeSearchResultDTO search(Long userId, String keyword, int page, int size) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserHandler(ErrorStatus.USER_NOT_FOUND));

        // 만료된 기록 삭제
        shoeSearchHistoryRepository.deleteExpiredHistories(userId, LocalDateTime.now());

        // 같은 키워드 있으면 삭제 후 재저장 (최신화)
        if (shoeSearchHistoryRepository.existsByUserIdAndKeyword(userId, keyword)) {
            shoeSearchHistoryRepository.deleteByUserIdAndKeyword(userId, keyword);
        }

        // 10개 초과 시 가장 오래된 기록 삭제
        if (shoeSearchHistoryRepository.countByUserId(userId) >= MAX_HISTORY_COUNT) {
            shoeSearchHistoryRepository.deleteOldestHistory(userId);
        }

        // 검색 기록 저장
        shoeSearchHistoryRepository.save(
                ShoeSearchConverter.toShoeSearchHistory(user, keyword)
        );

        // 신발 검색 (페이지네이션 적용)
        Pageable pageable = PageRequest.of(page, size);
        Page<Shoe> shoePage = shoeRepository
                .findByShoeNameContainingOrBrandNameContaining(keyword, keyword, pageable);

        return ShoeSearchConverter.toShoeSearchResultDTO(shoePage);
    }

    @Override
    @Transactional(readOnly = true)
    public ShoeSearchResponseDTO.ShoeSearchResultDTO searchSuggestions(String keyword, int page, int size) {

        // 검색 기록을 저장하지 않는 연관 검색어용 조회 (전체 DB 기준)
        Pageable pageable = PageRequest.of(page, size);
        Page<Shoe> shoePage = shoeRepository
                .findByShoeNameContainingOrBrandNameContaining(keyword, keyword, pageable);

        return ShoeSearchConverter.toShoeSearchResultDTO(shoePage);
    }

    @Override
    @Transactional(readOnly = true)
    public ShoeSearchResponseDTO.ShoeSearchHistoryResultDTO getSearchHistory(Long userId) {

        // 만료되지 않은 최근 10개 기록 조회
        List<ShoeSearchHistory> histories = shoeSearchHistoryRepository
                .findByUserIdOrderBySearchedAtDesc(userId)
                .stream()
                .filter(h -> h.getExpiresAt().isAfter(LocalDateTime.now()))
                .limit(MAX_HISTORY_COUNT)
                .collect(Collectors.toList());

        return ShoeSearchConverter.toShoeSearchHistoryResultDTO(histories);
    }

    @Override
    public void deleteSearchHistory(Long userId, Long historyId) {
        ShoeSearchHistory history = shoeSearchHistoryRepository.findByIdAndUserId(historyId, userId)
                .orElseThrow(() -> new ShoeHandler(ErrorStatus.SHOE_SEARCH_HISTORY_NOT_FOUND));
        shoeSearchHistoryRepository.delete(history);
    }

    @Override
    public ShoeResponseDTO.ShoeDetailResultDTO getShoeDetail(Long userId, Long shoeId) {

        Shoe shoe = shoeRepository.findById(shoeId)
                .orElseThrow(() -> new ShoeHandler(ErrorStatus.SHOE_NOT_FOUND));

        if (userId == null) {
            return ShoeConverter.toShoeDetailResultDTO(shoe, null, List.of());
        }

        ShoeRecommendation recommendation = shoeRecommendationRepository
                .findByUserIdAndShoeId(userId, shoeId)
                .orElse(null);

        if (recommendation == null) {
            return ShoeConverter.toShoeDetailResultDTO(shoe, null, List.of());
        }

        // reason 조회 (연관된 리뷰 3개 포함)
        List<ShoeRecommendationReason> reasons = shoeRecommendationReasonRepository
                .findByShoeRecommendationId(recommendation.getId());

        List<ShoeResponseDTO.ReasonResultDTO> reasonResultDTOs = reasons.stream()
                .map(ShoeConverter::toReasonResultDTO)
                .collect(Collectors.toList());

        return ShoeConverter.toShoeDetailResultDTO(shoe, recommendation, reasonResultDTOs);
    }
}
