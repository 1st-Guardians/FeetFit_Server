package com.feetfit.server.service.ShoeService;

import com.feetfit.server.apiPayload.code.status.ErrorStatus;
import com.feetfit.server.apiPayload.exception.handler.UserHandler;
import com.feetfit.server.converter.ShoeSearchConverter;
import com.feetfit.server.domain.Shoe;
import com.feetfit.server.domain.ShoeSearchHistory;
import com.feetfit.server.domain.User;
import com.feetfit.server.repository.ShoeRepository;
import com.feetfit.server.repository.ShoeSearchHistoryRepository;
import com.feetfit.server.repository.UserRepository;
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
}