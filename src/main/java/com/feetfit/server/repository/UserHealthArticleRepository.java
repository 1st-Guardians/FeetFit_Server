package com.feetfit.server.repository;

import com.feetfit.server.domain.UserHealthArticle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserHealthArticleRepository extends JpaRepository<UserHealthArticle, Long> {
}
