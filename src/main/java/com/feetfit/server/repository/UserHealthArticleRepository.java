package com.feetfit.server.repository;

import com.feetfit.server.domain.UserHealthArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserHealthArticleRepository extends JpaRepository<UserHealthArticle, Long> {

    @Query("""
            SELECT userHealthArticle
            FROM UserHealthArticle userHealthArticle
            JOIN FETCH userHealthArticle.healthArticle healthArticle
            WHERE userHealthArticle.user.id = :userId
            ORDER BY healthArticle.publishedAt DESC
            """)
    List<UserHealthArticle> findAllByUserIdWithArticle(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM UserHealthArticle userHealthArticle
            WHERE userHealthArticle.user.id = :userId
            """)
    void deleteByUserId(@Param("userId") Long userId);
}
