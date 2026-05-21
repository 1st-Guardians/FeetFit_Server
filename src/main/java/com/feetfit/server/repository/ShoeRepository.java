package com.feetfit.server.repository;

import com.feetfit.server.domain.Shoe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShoeRepository extends JpaRepository<Shoe, Long> {

    // 별점순
    Page<Shoe> findAllByOrderByOverallRatingDesc(Pageable pageable);

    // 관심도순
    Page<Shoe> findAllByOrderByClickCountDesc(Pageable pageable);

    // 발 적합도순 (해당 유저의 fitScore 기준)
    @Query("SELECT s FROM Shoe s " +
            "JOIN ShoeRecommendation r ON r.shoe.id = s.id " +
            "WHERE r.user.id = :userId " +
            "ORDER BY r.fitScore DESC")
    Page<Shoe> findAllByFitScoreDesc(@Param("userId") Long userId, Pageable pageable);

    // 신발 클릭 수 증가
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Shoe s SET s.clickCount = s.clickCount + 1 WHERE s.id = :shoeId")
    void incrementClickCount(@Param("shoeId") Long shoeId);

    // 신발명, 브랜드명 기준 검색
    Page<Shoe> findByShoeNameContainingOrBrandNameContaining(String shoeName, String brandName, Pageable pageable);

    // 추천 신발 3종
    @Query("SELECT s FROM Shoe s " +
            "JOIN ShoeRecommendation r ON r.shoe.id = s.id " +
            "WHERE r.user.id = :userId " +
            "ORDER BY r.fitScore DESC, s.id ASC")
    List<Shoe> findTop3ByFitScoreDesc(@Param("userId") Long userId, Pageable pageable);
}