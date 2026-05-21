package com.feetfit.server.repository;

import com.feetfit.server.domain.ShoeSearchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ShoeSearchHistoryRepository extends JpaRepository<ShoeSearchHistory, Long> {

    // 유저의 검색 기록 최신순 조회
    List<ShoeSearchHistory> findByUserIdOrderBySearchedAtDesc(Long userId);

    // 만료된 기록 삭제
    @Modifying
    @Query("DELETE FROM ShoeSearchHistory h WHERE h.user.id = :userId AND h.expiresAt < :now")
    void deleteExpiredHistories(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    // 10개 초과 시 가장 오래된 기록 삭제
    @Modifying
    @Query(value = "DELETE FROM shoe_search_history " +
            "WHERE user_id = :userId " +
            "AND searched_at = (SELECT searched_at FROM (SELECT searched_at FROM shoe_search_history WHERE user_id = :userId ORDER BY searched_at ASC LIMIT 1) t)",
            nativeQuery = true)
    void deleteOldestHistory(@Param("userId") Long userId);

    // 유저의 검색 기록 수 조회
    int countByUserId(Long userId);

    // 같은 키워드 기록 존재 여부
    boolean existsByUserIdAndKeyword(Long userId, String keyword);

    // 같은 키워드 기록 삭제 (재검색 시 갱신)
    @Modifying
    @Query("DELETE FROM ShoeSearchHistory h WHERE h.user.id = :userId AND h.keyword = :keyword")
    void deleteByUserIdAndKeyword(@Param("userId") Long userId, @Param("keyword") String keyword);
}