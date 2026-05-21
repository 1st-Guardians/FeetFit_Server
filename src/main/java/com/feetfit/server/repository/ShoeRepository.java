package com.feetfit.server.repository;

import com.feetfit.server.domain.Shoe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}